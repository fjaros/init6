package cz.zoom.fjaros.hackathon

import java.sql.Timestamp

import org.apache.log4j.{Level, Logger}
import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.mllib.feature.StandardScaler
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.{LinearRegressionWithSGD, LabeledPoint}
import org.apache.spark.mllib.stat.Statistics
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{SQLContext, Row, DataFrame}

import scala.collection.SortedMap
import scala.reflect.io.File

/**
 * Some Default Spark Settings
 */
sealed trait SparkApp {
  val sparkConf = new SparkConf()
    .setAppName("ZoomHackathon")
    .setMaster("local[8]")
  val sc = new SparkContext(sparkConf)
  val sqlContext = new SQLContext(sc)

  Logger.getLogger("org.apache.spark").setLevel(Level.OFF)
  Logger.getLogger("org.eclipse.jetty.server").setLevel(Level.OFF)
}

/**
 * Apache Spark showcase
 *
 * @author fjaros
 */
object Hackathon extends SparkApp {

  def main(args: Array[String]) {
    val url = "jdbc:postgresql://localhost:5432/callrec"
    val username = "postgres"

    /* Sparkify! */
    val couplesDf = sqlContext.read.format("jdbc").options(Map("url" -> (url + "?user=" + username), "dbtable" -> "couples")).load()
    val extDataDf = sqlContext.read.format("jdbc").options(Map("url" -> (url + "?user=" + username), "dbtable" -> "couple_extdata")).load()

//    couplesDf.show()
//    extDataDf.show()
//    exit

    // For my assignment, I wish to calculate for each agent's SPANLESS_REC_ID(their agent id) their average handling time and number of calls per month.
    val agentStatsRdd: RDD[Row] = inTraditionalSpark(couplesDf.rdd, extDataDf.rdd)
    File("outputDir").deleteRecursively()
    agentStatsRdd.saveAsTextFile("outputDir")

    println("Agent Stats Data Set:")
    agentStatsRdd.collect().foreach(println)

    //println(s"Correlation that agent number of calls per month is linear: ${linearCorrelation(agentStatsRdd)}")

    //println(s"Predict average call length of an agent in September: ${linearRegression(agentStatsRdd, 6 /* September */)} seconds")
  }

  def linearCorrelation(agentStats: RDD[Row]) = {
    val months = Array[Double](1, 2, 3, 4, 5, 6)
    def emptyArray = Array.fill[Double](months.length)(0)

    val callsPerMonth = agentStats.map(row => row.getAs[Map[String, LengthAndCount]](1))
      .aggregate(emptyArray)((result, mapWithCount) => {
      mapWithCount.foreach(entry => {
        result(entry._1 match {
          case "2015-03" => 0
          case "2015-04" => 1
          case "2015-05" => 2
          case "2015-06" => 3
          case "2015-07" => 4
          case "2015-08" => 5
          case _ => -1 // ???
        }) += entry._2.count
      })
      result
    }, (x, y) => x.zip(y).map(tuple => tuple._1 + tuple._2)
      )

    // Sparkify!
    val linearMonths = sc.parallelize(months)
    val callsPerMonthRdd = sc.parallelize(callsPerMonth)

    Statistics.corr(callsPerMonthRdd, linearMonths)
  }

  def linearRegression(agentStats: RDD[Row], month: Int) = {
    // Predict the average handling time per agent for the given month
    // Linear Regression with Stochastic Gradient Descent
    val dataSet = agentStats.map(row => row.getAs[Map[String, LengthAndCount]](1))
      .flatMap(_.map(entry => {
      LabeledPoint(entry._2.length,
        Vectors.dense(entry._1 match {
          case "2015-03" => 0
          case "2015-04" => 1
          case "2015-05" => 2
          case "2015-06" => 3
          case "2015-07" => 4
          case "2015-08" => 5
          case _ => -1 // ???
        }))
    })
      ).cache()

    val linearReg = new LinearRegressionWithSGD()
    linearReg.setIntercept(true)
    linearReg.optimizer
      .setNumIterations(1000)
      .setStepSize(25) // This is kind of the "Average" step that we expect for our data
    val scaler = new StandardScaler(withMean = true, withStd = true).fit(dataSet.map(_.features))
    val model = linearReg.run(dataSet.map(point => LabeledPoint(point.label, scaler.transform(point.features))))

    model.predict(scaler.transform(Vectors.dense(month)))
  }

  // Helper case class for combineByKey. We could use a tuple but this is more readable.
  case class LengthAndCount(length: Double, count: Long)

  def inTraditionalSpark(couplesRdd: RDD[Row], extDataRdd: RDD[Row]): RDD[Row] = {
    // Basically what Spark SQL does under the hood.
    def parseMonth(row: Row) = row.getAs[Timestamp]("start_ts").toString.substring(0, 7)

    // Create keyd RDDs (couple id -> couple)
    val keyedCouples = couplesRdd.map(row => (row.getAs[Long]("id"), row))
    val keyedExtDataValue = extDataRdd
      // We're only interested in ext data SPANLESS_REC_ID
      .filter(row => row.getAs[String]("key") == "SPANLESS_REC_ID")
      // And only fields cplid, and value
      .map(row => (row.getAs[Long]("cplid"), row.getAs[String]("value")))

    // Join them on couple id
    keyedExtDataValue.join(keyedCouples) // [SEP_FILIP_JAROS,[1234,09-02-2015 09:30:00,09-02-2015 09:35:00,...]]
      // Get rid of couple id, we don't need it anymore.
      .map(_._2) // underscore just means "this"

      // Calculate average couple length and number of couples handled per month
      .combineByKey( // This is a GROUP BY action
        // Initialize. This will create an initializer function the FIRST time the KEY is seen in EACH PARTITION (NOT THE FIRST TIME IT IS SEEN IN THE RDD)
        (row: Row) => {
          SortedMap[String, LengthAndCount](parseMonth(row) -> LengthAndCount(row.getAs[Int]("length"), 1L))
        },
        // The key is seen again in THIS partition. Add the length and count to the initialized accumulator.
        (value: SortedMap[String, LengthAndCount], row: Row) => {
          val parsedMonth = parseMonth(row)
          value + value.get(parsedMonth)
            .fold(parsedMonth -> LengthAndCount(row.getAs[Int]("length"), 1L))(entry =>
              parsedMonth -> LengthAndCount(entry.length + row.getAs[Int]("length"), entry.count + 1L)
            )
        },
        // The key exists in multiple partitions. Use this function to merge the accumulated values of each partition together.
        (value1: SortedMap[String, LengthAndCount], value2: SortedMap[String, LengthAndCount]) => value1 ++ value2
      )

      // Now we have Rows such as: Row(SEP_FILIP_JAROS, Map("2015-03" -> Length = 52731.4, Count = 1000)
      // We must take the average!
      .map(row => Row(row._1, row._2.map(monthStats => monthStats._1 -> LengthAndCount(monthStats._2.length / monthStats._2.count, monthStats._2.count))))

      // Sort by agent name
      .sortBy(_.getString(0))

    // We just found the average length of call of each agent. For the whole db in a Map->Reduce fashion.
  }

  def inSparkSQLWithSQL(couplesDf: DataFrame, extDataDf: DataFrame) = {
    // So you want to use Spark SQL? Good choice.

    // Register tables into our SQLContext
    couplesDf.registerTempTable("couples")
    extDataDf.registerTempTable("couple_extdata")

    sqlContext.sql(
      "SELECT e.value, avg(c.length), count(e.value) FROM couples c JOIN " +
        "(SELECT e.cplid, e.value FROM couple_extdata e WHERE e.key = 'SPANLESS_REC_ID') e " +
        "ON c.id = e.cplid GROUP BY e.value ORDER BY e.value"
    )
  }

  def inSparkSQLProgramatically(couplesDf: DataFrame, extDataDf: DataFrame) = {
    // You can also programatically execute your SQL query.

    // First let's clean the external data DataFrame. We only care about SPANLESS_REC_ID
    val cleanExtDataDf = extDataDf
      .select("cplid", "key", "value")
      .where(extDataDf("key") === "SPANLESS_REC_ID")

    couplesDf
      // Join the data frames on id = cplId
      .join(cleanExtDataDf, couplesDf("id") === cleanExtDataDf("cplid"))
      // Group By SPANLESS_REC_ID value
      .groupBy("value")
      // Run Aggregation
      // NOTE: As of Spark 1.4.1, Spark SQL does NOT support UDAFs (User Defined Aggregate Functions)
      // Meaning: if you would like to do anything on a Grouped Data set beyond avg/min/max/functions supported by Spark SQL
      // You cannot. You will have to drop out of Spark SQL into regular Spark and do it manually via reduce/combine functions.
      .agg("length" -> "avg")
      // Sort by agent name
      .orderBy("value")
  }
}
