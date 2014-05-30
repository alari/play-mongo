name := "play-mongo"

organization := "play-infra"

version := "0.2"

libraryDependencies ++= Seq(
    "play-infra" %% "play-wished" % "0.1",
    "org.reactivemongo" %% "play2-reactivemongo" % "0.10.2"
)

play.Project.playScalaSettings

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

