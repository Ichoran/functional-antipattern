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

  // Needed for Thyme snapshot
  def repositories() = super.repositories ++ Seq(
    coursier.maven.MavenRepository("https://oss.sonatype.org/content/repositories/snapshots")
  )

  def ivyDeps = Agg(
    ivy"com.github.ichoran::thyme:0.1.2-SNAPSHOT",  // Benchmarking
    ivy"org.typelevel::cats-core:1.1.0"
  )
}

object StateRandom extends Common {}
