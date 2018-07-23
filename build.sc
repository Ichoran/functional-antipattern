import mill._
import mill.scalalib._

trait Common extends ScalaModule {
  def scalaVersion = "2.12.6"
  def scalacOptions = T{ Seq(
    "-unchecked",
    "-feature",
    "-deprecation",
    "-opt:l:method",
    "-Ypartial-unification"
  )}

  def ivyDeps = Agg(
    ivy"org.typelevel::cats-core:1.1.0"
  )
}

object StateRandom extends Common {}
