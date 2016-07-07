package zkjz.hjr.SQL

import org.apache.spark.{SparkContext, SparkConf}

/**
  * Created by Administrator on 2016/7/1.
  */
object zkjzCheck {
  def main(args:Array[String]): Unit ={
    //初始化配置
    val conf = new SparkConf().setAppName("check_zkjz_hjr")
    val sc = new SparkContext(conf)

    //加载文件
    val outclinical_diago_rdd = sc.textFile("hdfs://10.2.8.11:8020/user/hive/warehouse/word/p*")
    val outclinical_words_rdd = sc.textFile("hdfs://10.2.8.11:8020/user/hive/warehouse/words/words0.csv")

    //将词库表转化为数组
    val counts_word = outclinical_words_rdd.toArray()

    //将转化为数组的词库表放入集合中
    var diag = ""
    var words =""
    var map = Map(diag -> words)//(0)将词库表转化为一个Map（门诊诊断->ICD_CODE）
    for(i <- 0 to counts_word.length-1){
      var line = counts_word(i)

        diag = line.split("\t")(0)
        words = line.split("\t")(1)
        map += (diag -> words)
    }
    //处理门诊表的业务逻辑
    val outclinical = outclinical_diago_rdd.filter(_.split("\001").length == 4).map(line =>{
      var strs = line.split("\001")
      var l = strs(3)
      var firstline = strs(0)+"\001"+strs(1)+"\001"+strs(2)+"\001"//（门诊表业务逻辑---2）拿出前三个字段
      var lastline = ""
      var s = line
      var m = l.length
      while (m >= 1) {
      var j = 0
        while (j < l.length - m + 1) {
          var s3 = l.substring(j, j + m)
          if (map.contains(s3)) {
            //s=s.replace(s3,map(s3)+" ")
            lastline += map(s3)+","
            l = l.replace(s3, "")
          }
          j = j + 1
        }
        m = m - 1
      }
      //firstline+lastline
      //数据校验
      line+"\001"+lastline
    })
    //结果校验
    /**
      * ReduceByKey过程
      */
    val pairs = outclinical.filter(_.split("\001").length == 4).map(line => {

      // if(line.split("\001").length == 4) {
      val col2 = line.split("\001")
      (col2(1) + "\001" + col2(2), col2(3))
      //}
    })

    //reduceByKey过程举例： val counts = pairs.reduceByKey((a, b) => a + b)
    val result = pairs.reduceByKey((a,b) => a+b).map(line =>{
      line._1 +"\001"+ line._2
    })

    // 1 第三列去重后的新RDD
    val column3RDD = result.map(line =>line.split("\001")(2)).map(line =>{
      var list = line.split(",").toList.distinct
      var str = ""
      for (i <- 0 to list.length-1){
        str += list(i)+","
      }
      str
    })
    // 2 使用zip算子合并两个RDD结果
    val zipResult = result.map(line =>{
      line.split("\001")(0) +"\t"+ line.split("\001")(1)
    }).zip(column3RDD).map(line => line._1 +"\t"+ line._2)
    //zipResult.foreach(println)

    val codes = zipResult.map(line => {
      var newline = line.split("\t")
      var firstline = newline(0)+"\t"+newline(1)+"\t"
      var codeline = newline(2).split(",")

      var lastline = ""
      for(i <- 0 to codeline.length-1){
        lastline += firstline+codeline(i)+"\n"
      }
      lastline
    }).filter(_.split("\n")!=null)

    //写入hdfs
    //codes.repartition(1).saveAsTextFile("hdfs://10.2.8.11:8020/user/hive/warehouse/hjr/results")
    outclinical.repartition(1).saveAsTextFile("hdfs://10.2.8.11:8020/user/hive/warehouse/hjr/zkjz_checkResults")
    //关服务
    sc.stop()
  }
}
