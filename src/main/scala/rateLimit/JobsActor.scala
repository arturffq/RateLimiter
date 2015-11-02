package rateLimit

import java.util.concurrent.ConcurrentLinkedQueue

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, ActorRef, ActorSystem}

/**
 * Tracks the rate limits for jobs on a single entity, and informs
 * replies to requests informing at the moment a job can be started.
 * Rates consist of intervals of time in milliseconds and a frequency of jobs.
 *
 * @param rates A sequence of rates, note that the
 */
class JobsActor(rates: Seq[Rate], system: ActorSystem) extends Actor {

  case class Refresh()
  class InternalJob(val uniqueId: Long, val time: Long) {
    def this(job: Job) = this(job.uniqueId, job.time)
    override def equals(obj: scala.Any): Boolean =  obj match {
      case job: Job => job.uniqueId == this.uniqueId
      case _ => false
    }
    override def toString = {
      s"InternalJob(uniqueId=$uniqueId, time=$time)"
    }
  }
  object InternalJob {
    def apply(uniqueId: Long, time: Long) = new InternalJob(uniqueId, time)
  }


  var jobId = 0L
  val currentQ = new ConcurrentLinkedQueue[Long]()
  val jobsQ = new ConcurrentLinkedQueue[InternalJob]()
  val timeQs: Seq[(Rate, ConcurrentLinkedQueue[InternalJob])] =
    for (r <- rates) yield (r, new ConcurrentLinkedQueue[InternalJob])
  val jobsMap: collection.mutable.Map[Long, ActorRef] = collection.mutable.Map()


  val nextPeek = rates.map(_.time).min
  var nextRefresh = system.scheduler.scheduleOnce(nextPeek millis){
    self ! Refresh()
  }
  private def updateRefreshSchedule() = {
    nextRefresh =
      system.scheduler
        .scheduleOnce(nextPeek millis) {self ! Refresh()}
  }

  override def receive: Receive = {

    case WantJob() =>
      jobId += 1
      jobsMap += jobId -> sender()
      jobsQ add InternalJob(jobId, System.currentTimeMillis)
      refresh()
    case DoneJob(id) =>
      jobsMap -= id
      this finalizeJob Job(id, System.currentTimeMillis)
      refresh()
    case Refresh() =>
      refresh()
      //println(s"Refreshing! Jobs on the queue ${jobsQ.size()}")
  }

  /**
   * Clean up the queues and add jobs to the current queue
   */
  def refresh() = {
    nextRefresh.cancel()
    def canAdd: Boolean = {
      val onGoing = currentQ.size
      for((rate,queue) <- timeQs)
        if (queue.size + onGoing >= rate.freq)
          return false
      true
    }
    // Weed out irrelevant entries
    for((rate,queue) <- timeQs)
      while(!queue.isEmpty && queue.peek.time < System.currentTimeMillis - rate.time)
        queue.poll()

    // Allow for
    while( canAdd && !jobsQ.isEmpty ) {
      val job = jobsQ.poll()
      jobsMap get job.uniqueId match {
        case Some(target: ActorRef) =>
          val updatedJob = Job(job.uniqueId, System.currentTimeMillis)
          target ! updatedJob
          currentQ add updatedJob.uniqueId
        case None =>
          printf(s"[${System.currentTimeMillis}] JobsActor: " +
            s"There's a missing actor reference!(${job.uniqueId})")
      }
    }
    if (!jobsQ.isEmpty) {
      updateRefreshSchedule()
    }
  }

  /**
   * Move a job from the currently under way queue to the rate tracking queues
   * @param job job to move.
   */
  def finalizeJob(job: Job) = {
    currentQ.remove(job.uniqueId)
    for((_, q) <- timeQs) q add new InternalJob(job)
  }

}
