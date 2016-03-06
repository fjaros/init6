package com.vilenet.utils

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.Success

import scala.concurrent.ExecutionContext.Implicits.global


/**
  * Created by filip on 1/16/16.
  */
object FutureCollector {
  implicit def futureSeqToFutureCollector[A](futures: Iterable[Future[A]]): FutureCollector[A] = new FutureCollector[A](futures)
}

class FutureCollector[A](futures: Iterable[Future[A]]) {

  def collectResults[B](task: A => Option[B]): Future[Seq[B]] = {
    val returnPromise = Promise[Seq[B]]()

    if (futures.nonEmpty) {
      var counter = futures.size
      val returnSeq = new ArrayBuffer[B](counter)

      futures.foreach(future => {
        future.onComplete(futureState => {
          val valueOpt = futureState match {
            case Success(action) => task(action)
            case _ => None
          }
          synchronized {
            valueOpt.foreach(returnSeq += _)
            counter -= 1
            if (counter <= 0) {
              returnPromise.success(returnSeq)
            }
          }
        })
      })
    } else {
      returnPromise.success(Seq.empty)
    }

    returnPromise.future
  }
}
