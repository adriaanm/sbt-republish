import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import sbtassembly.Plugin.AssemblyKeys._

object SbtRepublish extends Build {

  val ReleaseRepository = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  val SnapshotRepository = "https://oss.sonatype.org/content/repositories/snapshots"

  val Deps = config("deps") hide

  val originalSbtVersion = SettingKey[String]("original-sbt-version")
  val publishLocally = SettingKey[Boolean]("publish-locally")

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.typesafe.sbt",
    version := "0.13.0-SNAPSHOT",
    scalaVersion := "2.10.1",
    originalSbtVersion <<= version { v => if (v.endsWith("SNAPSHOT")) "latest.integration" else v },
    resolvers <++= version { v => if (v.endsWith("SNAPSHOT")) Seq(Classpaths.typesafeSnapshots) else Seq.empty },
    resolvers += Classpaths.typesafeReleases,
    crossPaths := false,
    publishMavenStyle := true,
    publishLocally := false,
    publishTo <<= (version, publishLocally) { (v, local) =>
      if (local) Some(Resolver.file("m2", Path.userHome / ".m2" / "repository"))
      else if (v.endsWith("SNAPSHOT")) Some("snapshots" at SnapshotRepository)
      else Some("releases" at ReleaseRepository)
    },
    credentials += Credentials(Path.userHome / ".ivy2" / "sonatype-credentials"),
    publishArtifact in Test := false,
    homepage := Some(url("https://github.com/sbt/sbt")),
    licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),
    pomExtra := {
      <scm>
        <url>https://github.com/typesafehub/sbt-republish</url>
        <connection>scm:git:git@github.com:typesafehub/sbt-republish.git</connection>
      </scm>
      <developers>
        <developer>
          <id>harrah</id>
          <name>Mark Harrah</name>
          <url>https://github.com/harrah</url>
        </developer>
        <developer>
          <id>pvlugter</id>
          <name>Peter Vlugter</name>
          <url>https://github.com/pvlugter</url>
        </developer>
      </developers>
    },
    pomIncludeRepository := { _ => false },
    ivyConfigurations += Deps,
    externalResolvers <<= (externalResolvers, resolvers, publishLocally) map { (ers, rs, local) => if (local) Seq(Resolver.defaultLocal) ++ rs else ers }
  )

  lazy val sbtRepublish = Project(
    "sbt-republish",
    file("."),
    aggregate = Seq(sbtInterface, compilerInterface, incrementalCompiler),
    settings = buildSettings ++ Seq(
      publishArtifact := false,
      publishArtifact in makePom := false
    )
  )

  lazy val sbtInterface = Project(
    "sbt-interface",
    file("sbt-interface"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= originalSbtVersion { "org.scala-sbt" % "interface" % _ % Deps.name },
      packageBin in Compile <<= repackageDependency(packageBin, "interface")
    )
  )

  lazy val compilerInterface = Project(
    "compiler-interface",
    file("compiler-interface"),
    settings = buildSettings ++ Seq(
      libraryDependencies <+= originalSbtVersion { "org.scala-sbt" % "compiler-interface" % _ % Deps.name classifier "src" },
      packageSrc in Compile <<= repackageDependency(packageSrc, "compiler-interface"),
      publishArtifact in packageBin := false,
      publishArtifact in (Compile, packageSrc) := true
    )
  )

  lazy val incrementalCompiler = Project(
    "incremental-compiler",
    file("incremental-compiler"),
    dependencies = Seq(sbtInterface),
    settings = buildSettings ++ assemblySettings ++ Seq(
      libraryDependencies <+= originalSbtVersion { "org.scala-sbt" % "compiler-integration" % _ % Deps.name },
      libraryDependencies <+= scalaVersion { "org.scala-lang" % "scala-compiler" % _ },
      managedClasspath in Deps <<= (classpathTypes, update) map { (types, up) => Classpaths.managedJars(Deps, types, up) },
      fullClasspath in assembly <<= managedClasspath in Deps,
      assembleArtifact in packageScala := false,
      excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
        cp filter { jar => Set("scala-compiler.jar", "interface.jar") contains jar.data.getName }
      },
      mergeStrategy in assembly <<= (mergeStrategy in assembly)( default => {
        case "NOTICE" => MergeStrategy.first
        case x => default(x)
      }),
      packageBin in Compile <<= (assembly, artifactPath in packageBin in Compile) map {
        (assembled, packaged) => IO.copyFile(assembled, packaged, false); packaged
      }
    )
  )

  def repackageDependency(packageTask: TaskKey[File], jarName: String) = {
    (classpathTypes, update, artifactPath in packageTask in Compile) map {
      (types, up, packaged) => {
        val cp = Classpaths.managedJars(Deps, types, up)
        val jar = cp.find(_.data.getName startsWith jarName).get.data
        IO.copyFile(jar, packaged, false)
        packaged
      }
    }
  }

  def environment(property: String, env: String): Option[String] =
    Option(System.getProperty(property)) orElse Option(System.getenv(env))
}
