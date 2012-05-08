import sbt._
import sbt.Keys._
import sbtassembly.Plugin._
import sbtassembly.Plugin.AssemblyKeys._

object SbtRepublish extends Build {

  val SbtVersion = "0.13.0-SNAPSHOT"
  val PublishedVersion = "0.13.0-SNAPSHOT"
  val ScalaVersion = "2.9.2"

  val ReleaseRepository = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
  val SnapshotRepository = "https://oss.sonatype.org/content/repositories/snapshots"

  val Deps = config("deps") hide

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "com.typesafe.sbt",
    version := PublishedVersion,
    scalaVersion := ScalaVersion,
    crossPaths := false,
    publishMavenStyle := true,
    publishTo := Some(Resolver.file("m2", file(Path.userHome + "/.m2/repository"))),
    // publishTo <<= version { v =>
    //   if (v.trim.endsWith("SNAPSHOT")) Some("snapshots" at SnapshotRepository)
    //   else Some("releases" at ReleaseRepository)
    // },
    publishArtifact in Test := false,
    homepage := Some(url("http://www.typesafe.com")),
    licenses := Seq("BSD-style" -> url("http://www.opensource.org/licenses/bsd-license.php")),
    pomExtra := {
      <scm>
        <url>https://github.com/pvlugter/sbt-republish</url>
        <connection>scm:git:git@github.com:pvlugter/sbt-republish.git</connection>
      </scm>
      <developers>
        <developer>
          <id>harrah</id>
          <name>Mark Harrah</name>
          <url>http://www.typesafe.com</url>
        </developer>
        <developer>
          <id>pvlugter</id>
          <name>Peter Vlugter</name>
          <url>http://www.typesafe.com</url>
        </developer>
      </developers>
    },
    pomIncludeRepository := { _ => false },
    ivyConfigurations += Deps,
    externalResolvers <<= resolvers map { rs => Resolver.withDefaultResolvers(rs, scalaTools = false) }
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
      libraryDependencies += "org.scala-sbt" % "interface" % SbtVersion % Deps.name,
      packageBin in Compile <<= repackageDependency(packageBin, "interface.jar")
    )
  )

  lazy val compilerInterface = Project(
    "compiler-interface",
    file("compiler-interface"),
    settings = buildSettings ++ Seq(
      libraryDependencies += "org.scala-sbt" % "compiler-interface" % SbtVersion % Deps.name classifier "src",
      packageSrc in Compile <<= repackageDependency(packageSrc, "compiler-interface-src.jar"),
      publishArtifact in packageBin := false,
      publishArtifact in (Compile, packageSrc) := true
    )
  )

  lazy val incrementalCompiler = Project(
    "incremental-compiler",
    file("incremental-compiler"),
    dependencies = Seq(sbtInterface),
    settings = buildSettings ++ assemblySettings ++ Seq(
      libraryDependencies += "org.scala-sbt" % "compiler-integration" % SbtVersion % Deps.name,
      libraryDependencies += "org.scala-lang" % "scala-compiler" % ScalaVersion,
      managedClasspath in Deps <<= (classpathTypes, update) map { (types, up) => Classpaths.managedJars(Deps, types, up) },
      fullClasspath in assembly <<= managedClasspath in Deps,
      assembleArtifact in packageScala := false,
      excludedJars in assembly <<= (fullClasspath in assembly) map { cp =>
        cp filter { jar => Set("scala-compiler.jar", "interface.jar") contains jar.data.getName }
      },
      mergeStrategy in assembly := {
        case "NOTICE" => MergeStrategy.first
        case _ => MergeStrategy.deduplicate
      },
      packageBin in Compile <<= (assembly, artifactPath in packageBin in Compile) map {
        (assembled, packaged) => IO.copyFile(assembled, packaged, false); packaged
      }
    )
  )

  def repackageDependency(packageTask: TaskKey[File], jarName: String) = {
    (classpathTypes, update, artifactPath in packageTask in Compile) map {
      (types, up, packaged) => {
        val cp = Classpaths.managedJars(Deps, types, up)
        val jar = cp.find(_.data.getName == jarName).get.data
        IO.copyFile(jar, packaged, false)
        packaged
      }
    }
  }
}
