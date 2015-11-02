package rateLimit

import scala.concurrent.ExecutionContext.Implicits.global

object Main {

   def main (args: Array[String]) {

     println("Showcasing job rate limiter.")

     // Some job to execute, can not have arguments.
     // In this cae we're just printing the amount of
     // millis since the startTime was instantiated.
     val startTime = System.currentTimeMillis()
     val currentTime =
       () => System.currentTimeMillis()-startTime

     // A sequence of all rates respected,
     // a rate is an interval or time(ms)
     // and the number of occurrences allowed
     // in said interval.
     val rates = Seq(Rate(100, 1), Rate(1000, 5))

     // Instantiate the rate limiter, the rates are
     // fixed, so create a new one to limit a different
     // collection fo rates.
     val jobRateLimiter = new JobRateLimiter(rates)

     // Let's ask for 100 jobs, and then collect
     // their results.
     // Tasks are executed asynchronously so you
     // get futures with the values.
     val futTimes = for( i <- 1 to 100 ) yield {
         jobRateLimiter.execute(currentTime)
     }

     // Let's make some use of all collected values.
     futTimes foreach { (futureTime) =>
       for (time <- futureTime)
         println(s"Processed $time ms.")
     }
   }

 }
