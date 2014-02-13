package typesafe


class HrefRegex {

}


object HrefRegex {


  val LinkRegex = """(?i)<a([^>]+)>(.+?)</a>""".r
  val UrlRegex = """(?i)\s*href\s*=\s*(\"([^"]*)\")""".r

  val testHtml = """<a href="http://daily-scala.blogspot.com/2010/01/overcoming-type-erasure-in-matching-2.html">Overcoming Type Erasure in Matching 2 (Variance)</a>"""
  val l = """href="http://daily-scala.blogspot.com/2010/01/overcoming-type-erasure-in-matching-2.html""""
  val m = """ href = "/2010/01/overcoming-type-erasure-in-matching-2.html""""
  val n = """href =  "http://daily-scala.blogspot.com/2010/01/overcoming-type-erasure-in-matching-2.html""""

  val LinkRegex(one,two) = testHtml
  val UrlRegex(_,url) = one
}