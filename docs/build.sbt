lazy val docs = (project in file(".")).
  enablePlugins(ParadoxPlugin)

// Uses the out of the box generic theme.
paradoxTheme := Some(builtinParadoxTheme("generic"))

scalaVersion := "2.11.8"
