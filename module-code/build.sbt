name := "play-mongo"

organization := "play-infra"

version := "0.4.0-SNAPSHOT"

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
    "play-infra" %% "play-wished" % "0.3.0",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23"
)

scalaVersion := "2.11.6"

crossScalaVersions := Seq("2.11.6")

lazy val root = (project in file(".")).enablePlugins(play.PlayScala)

resolvers ++= Seq(
  "quonb" at "http://repo.quonb.org/"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-language:_",
  "-encoding", "UTF-8"
)

publishTo := Some(Resolver.file("file",  new File( "/mvn-repo" )) )

testOptions in Test += Tests.Argument("junitxml")

