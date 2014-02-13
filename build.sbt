name := "urlScraper"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  "com.typesafe.akka" %% "akka-actor" % "2.2.1",
  "com.typesafe.akka" %% "akka-contrib" % "2.2.1",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.1",
  "com.typesafe.akka" %% "akka-cluster" % "2.2.1",
  "org.scalatest" %% "scalatest" % "1.9.1" % "test",
  "org.webjars" %% "webjars-play" % "2.2.0",
  "org.webjars" % "bootstrap" % "2.3.1"
)     

play.Project.playScalaSettings
