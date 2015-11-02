package rateLimit

import akka.actor.{Props, ActorSystem}

import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

class JobRateLimiter(rates: Seq[Rate],
                     actorSystem: ActorSystem = ActorSystem("JobRate")) {

  val jobsActor = actorSystem.actorOf(Props(new JobsActor(rates, actorSystem)))

  def execute[T]( job: ()=>T): Future[T] = {
    val jobFinished = Promise[Job]()
    val canDoJob = Promise[Job]()
    var response: Option[T] = None
    val promise = Promise[T]()

    canDoJob.future onSuccess {
      case aJob =>
        val result = job()
        jobFinished.success(Job(aJob.uniqueId, System.currentTimeMillis))
        promise.success(result)
    }

    val jobHandler = actorSystem.actorOf(Props(new JobHandler(jobFinished.future, jobsActor, canDoJob)))
    jobHandler ! WantJob()

    promise.future
  }

}
