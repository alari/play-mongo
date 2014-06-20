name := "play-mongo"

organization := "play-infra"

version := "0.3.1"

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
    "play-infra" %% "play-wished" % "0.3.0",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.akka23-SNAPSHOT"
)

crossScalaVersions := Seq("2.10.4", "2.11.1")

lazy val root = (project in file(".")).enablePlugins(play.PlayScala)

resolvers ++= Seq(
  "quonb" at "http://repo.quonb.org/"
)

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

publishTo := Some(Resolver.file("file",  new File( "/mvn-repo" )) )

testOptions in Test += Tests.Argument("junitxml")

