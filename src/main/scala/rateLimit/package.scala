package object rateLimit {

  case class WantJob()
  case class Rate(time: Long, freq: Long)
  case class Job(uniqueId: Long, time: Long)
  case class DoneJob(uniqueId: Long)

}
