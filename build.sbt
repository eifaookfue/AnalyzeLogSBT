name := "AnalyzeLogSBT"

version := "0.1"

scalaVersion := "2.12.8"

// https://mvnrepository.com/artifact/commons-io/commons-io
libraryDependencies += "commons-io" % "commons-io" % "2.6"
libraryDependencies ++= Seq (
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.slick" %% "slick" % "3.3.0",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.3.0",
  "com.h2database" % "h2" % "1.4.191"
)