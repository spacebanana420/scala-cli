import $ivy.`com.lihaoyi::mill-contrib-bloop:$MILL_VERSION`
import $ivy.`io.get-coursier::coursier-launcher:2.0.16`
import $file.project.deps, deps.{Deps, Scala}
import $file.project.ghreleaseassets
import $file.project.publish, publish.ScalaCliPublishModule
import $file.project.settings, settings.{CliLaunchers, HasTests, LocalRepo, PublishLocalNoFluff, localRepoResourcePath}

import java.io.File

import de.tobiasroeser.mill.vcs.version.VcsVersion
import mill._, scalalib.{publish => _, _}

import scala.util.Properties


// Tell mill modules are under modules/
implicit def millModuleBasePath: define.BasePath =
  define.BasePath(super.millModuleBasePath.value / "modules")


object cli                    extends Cli
object build                  extends Cross[Build]             (defaultScalaVersion)
object stubs                  extends JavaModule with ScalaCliPublishModule with PublishLocalNoFluff
object runner                 extends Cross[Runner]            (Scala.all: _*)
object `test-runner`          extends Cross[TestRunner]        (Scala.all: _*)
object bloopgun               extends Cross[Bloopgun]          (Scala.allScala2: _*)
object `line-modifier-plugin` extends Cross[LineModifierPlugin](Scala.all: _*)
object `tasty-lib`            extends Cross[TastyLib]          (Scala.all: _*)

object integration extends Module {
  object jvm    extends JvmIntegration
  object native extends NativeIntegration
}


// We should be able to switch to 2.13.x when bumping the scala-native version
def defaultScalaVersion = Scala.scala212

class Build(val crossScalaVersion: String) extends CrossSbtModule with ScalaCliPublishModule with HasTests {
  def moduleDeps = Seq(
    bloopgun(),
    `tasty-lib`(),
    `test-runner`()
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.asm,
    Deps.bloopConfig,
    Deps.coursierJvm
      // scalaJsEnvNodeJs brings a guava version that conflicts with this
      .exclude(("com.google.collections", "google-collections")),
    Deps.coursierLauncher,
    Deps.dependency,
    Deps.jimfs, // scalaJsEnvNodeJs pulls jimfs:1.1, whose class path seems borked (bin compat issue with the guava version it depends on)
    Deps.jniUtils,
    Deps.nativeTestRunner,
    Deps.nativeTools,
    Deps.osLib,
    Deps.pprint,
    Deps.scalaJsEnvNodeJs,
    Deps.scalaJsLinker,
    Deps.scalaJsTestAdapter,
    Deps.scalametaTrees,
    Deps.scalaparse,
    Deps.swoval
  )

  def constantsFile = T{
    val dest = T.dest / "Constants.scala"
    val code =
      s"""package scala.build.internal
         |
         |/** Build-time constants. Generated by mill. */
         |object Constants {
         |  def version = "${publishVersion()}"
         |  def detailedVersion = "${VcsVersion.vcsState().format()}"
         |
         |  def scalaJsVersion = "${Deps.scalaJsLinker.dep.version}"
         |  def scalaNativeVersion = "${Deps.nativeTools.dep.version}"
         |
         |  def stubsOrganization = "${stubs.pomSettings().organization}"
         |  def stubsModuleName = "${stubs.artifactName()}"
         |  def stubsVersion = "${stubs.publishVersion()}"
         |
         |  def testRunnerOrganization = "${`test-runner`(defaultScalaVersion).pomSettings().organization}"
         |  def testRunnerModuleName = "${`test-runner`(defaultScalaVersion).artifactName()}"
         |  def testRunnerVersion = "${`test-runner`(defaultScalaVersion).publishVersion()}"
         |  def testRunnerMainClass = "${`test-runner`(defaultScalaVersion).mainClass().getOrElse(sys.error("No main class defined for test-runner"))}"
         |
         |  def runnerOrganization = "${runner(defaultScalaVersion).pomSettings().organization}"
         |  def runnerModuleName = "${runner(defaultScalaVersion).artifactName()}"
         |  def runnerVersion = "${runner(defaultScalaVersion).publishVersion()}"
         |  def runnerMainClass = "${runner(defaultScalaVersion).mainClass().getOrElse(sys.error("No main class defined for runner"))}"
         |
         |  def lineModifierPluginOrganization = "${`line-modifier-plugin`(defaultScalaVersion).pomSettings().organization}"
         |  def lineModifierPluginModuleName = "${`line-modifier-plugin`(defaultScalaVersion).artifactName()}"
         |  def lineModifierPluginVersion = "${`line-modifier-plugin`(defaultScalaVersion).publishVersion()}"
         |
         |  def semanticDbPluginOrganization = "${Deps.scalametaTrees.dep.module.organization.value}"
         |  def semanticDbPluginModuleName = "semanticdb-scalac"
         |  def semanticDbPluginVersion = "${Deps.scalametaTrees.dep.version}"
         |
         |  def localRepoResourcePath = "$localRepoResourcePath"
         |  def localRepoVersion = "${VcsVersion.vcsState().format()}"
         |}
         |""".stripMargin
    os.write(dest, code)
    PathRef(dest)
  }
  def generatedSources = super.generatedSources() ++ Seq(constantsFile())

  def localRepoJar = T{
    `local-repo`.localRepoJar()
  }

  object test extends Tests {
    def runClasspath = T{
      super.runClasspath() ++ Seq(localRepoJar())
    }
  }
}

trait Cli extends SbtModule with CliLaunchers with ScalaCliPublishModule with HasTests {
  def scalaVersion = defaultScalaVersion
  def moduleDeps = Seq(
    build(defaultScalaVersion)
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.caseApp,
    Deps.coursierInterfaceSvmSubs,
    Deps.metabrowseServer,
    Deps.slf4jNop,
    Deps.svmSubs
  )
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.svm
  )
  def mainClass = Some("scala.cli.ScalaCli")
  def nativeImageMainClass =
    if (Properties.isWin || Properties.isLinux) "scala.cli.ScalaCliLight"
    else "scala.cli.ScalaCli"

  def localRepoJar = `local-repo`.localRepoJar()
  def graalVmVersion = deps.graalVmVersion

  object test extends Tests
}

trait CliIntegration extends SbtModule with ScalaCliPublishModule with HasTests {
  def scalaVersion = sv
  def testLauncher: T[PathRef]
  def isNative = T{ false }

  def sv = Scala.scala213

  private def mainArtifactName = T{ artifactName() }
  object test extends Tests {
    def ivyDeps = super.ivyDeps() ++ Agg(
      Deps.osLib,
      Deps.pprint
    )
    def forkEnv = super.forkEnv() ++ Seq(
      "SCALA_CLI" -> testLauncher().path.toString,
      "IS_NATIVE_SCALA_CLI" -> isNative().toString
    )
    def sources = T.sources {
      val name = mainArtifactName().stripPrefix("integration-")
      super.sources().map { ref =>
        PathRef(os.Path(ref.path.toString.replace(File.separator + name + File.separator, File.separator)))
      }
    }
  }
}

trait NativeIntegration extends CliIntegration {
  def testLauncher = cli.nativeImage()
  def isNative = true
}

trait JvmIntegration extends CliIntegration {
  def testLauncher = cli.launcher()
}

class Runner(val crossScalaVersion: String) extends CrossSbtModule with ScalaCliPublishModule with PublishLocalNoFluff {
  def mainClass = Some("scala.cli.runner.Runner")
  def ivyDeps =
    if (crossScalaVersion.startsWith("3.") && !crossScalaVersion.contains("-RC"))
      Agg(Deps.prettyStacktraces)
    else
      Agg.empty[Dep]
  def repositories = super.repositories ++ Seq(
    coursier.Repositories.sonatype("snapshots")
  )
  def sources = T.sources {
    val scala3DirNames =
      if (crossScalaVersion.startsWith("3.")) {
        val name =
          if (crossScalaVersion.contains("-RC")) "scala-3-unstable"
          else "scala-3-stable"
        Seq(name)
      } else Nil
    val extraDirs = scala3DirNames.map(name => PathRef(millSourcePath / "src" / "main" / name))
    super.sources() ++ extraDirs
  }
}

class TestRunner(val crossScalaVersion: String) extends CrossSbtModule with ScalaCliPublishModule with PublishLocalNoFluff {
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.asm,
    Deps.testInterface
  )
  def mainClass = Some("scala.cli.testrunner.DynamicTestRunner")
}

class Bloopgun(val crossScalaVersion: String) extends CrossSbtModule with ScalaCliPublishModule {
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.bsp4j,
    Deps.coursierInterface,
    Deps.snailgun
  )
  def mainClass = Some("scala.build.bloop.bloopgun.Bloopgun")

  def constantsFile = T{
    val dest = T.dest / "Constants.scala"
    val code =
      s"""package scala.build.bloop.bloopgun.internal
         |
         |/** Build-time constants. Generated by mill. */
         |object Constants {
         |  def bloopVersion = "${Deps.bloopConfig.dep.version}"
         |  def bspVersion = "${Deps.bsp4j.dep.version}"
         |}
         |""".stripMargin
    os.write(dest, code)
    PathRef(dest)
  }
  def generatedSources = super.generatedSources() ++ Seq(constantsFile())
}

class LineModifierPlugin(val crossScalaVersion: String) extends CrossSbtModule with ScalaCliPublishModule with PublishLocalNoFluff {
  def compileIvyDeps =
    if (crossScalaVersion.startsWith("2."))
      Agg(Deps.scalac(crossScalaVersion))
    else
      Agg(Deps.scala3Compiler(crossScalaVersion))
}

class TastyLib(val crossScalaVersion: String) extends CrossSbtModule with ScalaCliPublishModule

object `local-repo` extends LocalRepo {
  def stubsModules = {
    val javaModules = Seq(
      stubs
    )
    val crossModules = for {
      sv <- Scala.all
      proj <- Seq(runner, `test-runner`, `line-modifier-plugin`)
    } yield proj(sv)
    javaModules ++ crossModules
  }
  def version = runner(defaultScalaVersion).publishVersion()
}


// Helper CI commands

def publishSonatype(tasks: mill.main.Tasks[PublishModule.PublishData]) = T.command {
  publish.publishSonatype(
    data = define.Task.sequence(tasks.value)(),
    log = T.ctx().log
  )
}

def copyLauncher(directory: String = "artifacts") = T.command {
  val nativeLauncher = cli.nativeImage().path
  ghreleaseassets.copyLauncher(nativeLauncher, directory)
}

def uploadLaunchers(directory: String = "artifacts") = T.command {
  val version = cli.publishVersion()
  ghreleaseassets.uploadLaunchers(version, directory)
}

def unitTests() = T.command {
  build(defaultScalaVersion).test.test()
}
