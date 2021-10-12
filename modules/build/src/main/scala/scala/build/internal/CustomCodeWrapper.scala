package scala.build.internal

case object CustomCodeWrapper extends CodeWrapper {
  def mainClassObject(className: Name): Name =
    Name(className.raw ++ "_sc")

  private val userCodeNestingLevel = 1
  def apply(
    code: String,
    pkgName: Seq[Name],
    indexedWrapperName: Name,
    extraCode: String
  ) = {
    val name = mainClassObject(indexedWrapperName).backticked
    val mainObjectCode = AmmUtil.normalizeNewlines(s"""|object $name {
                                                       |  private var argsOpt0 = Option.empty[Array[String]]
                                                       |  def setArgs(args: Array[String]): Unit = {
                                                       |    argsOpt0 = Some(args)
                                                       |  }
                                                       |  def argsOpt: Option[Array[String]] = argsOpt0
                                                       |  def args: Array[String] = argsOpt.getOrElse {
                                                       |    sys.error("No arguments passed to this script")
                                                       |  }
                                                       |  def main(args: Array[String]): Unit = {
                                                       |    setArgs(args)
                                                       |    ${indexedWrapperName.backticked}
                                                       |  }
                                                       |}
                                                       |""".stripMargin)

    val packageDirective =
      if (pkgName.isEmpty) "" else s"package ${AmmUtil.encodeScalaSourcePath(pkgName)}" + "\n"

    // indentation is important in the generated code, so we don't want scalafmt to touch that
    // format: off
    val top = AmmUtil.normalizeNewlines(s"""
$packageDirective


object ${indexedWrapperName.backticked} {
""")
    val bottom = AmmUtil.normalizeNewlines(s"""
  def args = $name.args
  $extraCode
}
$mainObjectCode
""")
    // format: on

    (top, bottom, userCodeNestingLevel)
  }
}
