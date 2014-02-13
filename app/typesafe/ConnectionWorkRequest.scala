package typesafe

case class ConnectionWorkRequest(workId: Int, url:String)

case class WorkResult(workId: Int, result: Any)