import org.apache.spark.SparkContext
import SparkContext._
import java.lang.Number
import java.lang.Integer

object PageRank {

  private def parseLineToIntDouble(line: String): (Int, Double) = {
    val splitted = line.split(" ")
    val key = splitted(0).toInt
    val dest = splitted(1).toDouble
    (key, dest)
  }

  private def parseLineToIntInt(line: String): (Int, Int) = {
    val splitted = line.split(" ")
    val key = splitted(0).toInt
    val dest = splitted(1).toInt
    (key, dest)
  }

  def main(args:Array[String]) {
    val edges_dir = args(0)
    val vertices_dir = args(1)
    val output_dir = args(2)
    val paralelism = args(3)

    System.setProperty("spark.default.parallelism", paralelism)
    System.setProperty("spark.worker.timeout", "60000")
    System.setProperty("spark.akka.timeout", "60000")
    System.setProperty("spark.storage.blockManagerHeartBeatMs", "60000")
    System.setProperty("spark.akka.storage.retry.wait", "60000")
    System.setProperty("spark.akka.frameSize", "10000")

    val sc = new SparkContext("spark://freestyle:7077", "main_spark",
      "/home/icg27/spark-0.9.0-incubating/",
      Seq("/home/icg27/Musketeer/tests/spark_pagerank/target/scala-2.10/pagerank_2.10-1.0.jar"))

    val NUM_OF_ITERATIONS = 5

    val input_edges = sc.textFile("hdfs://10.11.12.61:8020" + edges_dir)
    val input_pr = sc.textFile("hdfs://10.11.12.61:8020" + vertices_dir)

    val edges:org.apache.spark.rdd.RDD[(Int, Int)] = input_edges.map(parseLineToIntInt)
    var ranks:org.apache.spark.rdd.RDD[(Int, Double)] = input_pr.map(parseLineToIntDouble)

    val node_one:org.apache.spark.rdd.RDD[(Int, Int)] = edges.map({
      case (src, dst) =>
        (src, 1)
      case _ =>
        (0, 0)
    })
    val node_cnt:org.apache.spark.rdd.RDD[(Int, Int)] = node_one.reduceByKey(_+_)
    val edgescnt:org.apache.spark.rdd.RDD[(Int, (Int, Int))] = edges.join(node_cnt).cache()
    for (i <- 1 to NUM_OF_ITERATIONS) {
      val edgespr:org.apache.spark.rdd.RDD[(Int, ((Int, Int), Double))] = edgescnt.join(ranks)
      val contributions:org.apache.spark.rdd.RDD[(Int, Double)] = edgespr.map({
        case (src, ((dst, cnt), pr)) =>
          (src, ((dst, cnt), pr / cnt))
        case _ =>
          (0, ((0, 0), 1.0))
      }).map({
        case (src, ((dst, cnt), pr)) =>
          (dst, pr)
        case _ =>
          (0, 1.0)
      })
      val acc_contrib:org.apache.spark.rdd.RDD[(Int, Double)] = contributions.reduceByKey(_+_)
      ranks = acc_contrib.map({
        case (node, pr) =>
          (node, 0.15 + 0.85 * pr)
        case _ =>
          (0, 1.0)
      })
    }

    ranks.saveAsTextFile("hdfs://10.11.12.61:8020" + output_dir)
    System.exit(0)
  }

}