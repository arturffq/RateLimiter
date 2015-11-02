package rateLimit

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

import akka.actor.{Actor, ActorRef}

class JobHandler(finishFuture: Future[Job],
                 jobsActor: ActorRef,
                 promiseJob: Promise[Job]) extends Actor {
  val endJob = finishFuture onSuccess {
    case job =>
      jobsActor ! DoneJob(job.uniqueId)
  }
  override def receive: Receive = {
    case WantJob() =>
      jobsActor ! WantJob()
    case job :Job =>
      promiseJob.success(job)
      context.stop(self)
  }
}
