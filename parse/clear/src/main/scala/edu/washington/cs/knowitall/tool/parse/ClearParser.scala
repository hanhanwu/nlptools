package edu.knowitall
package tool
package parse

import scala.collection.JavaConverters._
import edu.knowitall.tool.tokenize.Tokenizer
import edu.knowitall.tool.tokenize.Token
import graph.Dependency
import edu.knowitall.tool.parse.graph.DependencyGraph
import edu.knowitall.tool.parse.graph.DependencyNode
import java.lang.ProcessBuilder
import java.io.PrintWriter
import com.googlecode.clearnlp.component.pos.CPOSTagger
import com.googlecode.clearnlp.component.dep.CDEPPassParser
import java.util.zip.ZipInputStream
import com.googlecode.clearnlp.nlp.NLPDecode
import com.googlecode.clearnlp.dependency.DEPTree
import com.googlecode.clearnlp.dependency.DEPNode
import edu.knowitall.tool.tokenize.ClearTokenizer
import edu.knowitall.common.Resource.using
import com.googlecode.clearnlp.component.morph.CEnglishMPAnalyzer
import edu.knowitall.tool.postag.Postagger
import edu.knowitall.tool.postag.ClearPostagger

class ClearParser(val postagger: Postagger = new ClearPostagger()) extends DependencyParser {
  val clearMorphaUrl = this.getClass.getResource("/edu/knowitall/tool/tokenize/dictionary-1.2.0.zip")
  require(clearMorphaUrl != null, "cannot find clear dict model")
  val clearMorpha = using(clearMorphaUrl.openStream()) { input =>
    new CEnglishMPAnalyzer(new ZipInputStream(input))
  }

  val clearDepUrl = this.getClass.getResource("/knowitall/models/clear/ontonotes-en-dep-1.3.0.jar")
  require(clearDepUrl != null, "cannot find clear dep model")
  val clearDepParser = using (clearDepUrl.openStream()) { input =>
    new CDEPPassParser(new ZipInputStream(input))
  }

  override def dependencyGraph(string: String) = {
    val tokens = postagger.postag(string)
    val tree = new DEPTree()
    tokens.zipWithIndex.foreach { case (token, i) =>
      val node = new DEPNode(i + 1, token.string)
      node.pos = token.postag
      tree.add(node)
    }

    clearMorpha.process(tree)
    clearDepParser.process(tree)

    ClearParser.graphFromTree(string, tree, tokens)
  }
}

object ClearParser {
  def graphFromTree(string: String, tree: DEPTree, tokens: Seq[Token]) = {
    val nodeMap = (for ((node, i) <- tree.iterator.asScala.drop(1).zipWithIndex) yield {
      node -> new DependencyNode(node.form, node.pos, i, tokens(i).offset)
    }).toMap

    val deps = for {
      sourceNode <- tree.iterator.asScala.drop(1).toList
      if sourceNode.hasHead
      val label = sourceNode.getLabel
      if label != "root"
      val destNode = sourceNode.getHead
    } yield {
      new Dependency(nodeMap(destNode), nodeMap(sourceNode), label)
    }

    new DependencyGraph(nodeMap.values, deps)
  }
}

object ClearDependencyParserMain extends DependencyParserMain {
  lazy val dependencyParser = new ClearParser()
}
