package com.vilenet.utils

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

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
        future.onComplete {
          case Success(action) =>
            synchronized {
              counter = appendResultAndCheckPromise(counter, task(action), returnSeq, returnPromise)
            }
          case Failure(ex) =>
            synchronized {
              counter = appendResultAndCheckPromise(counter, None, returnSeq, returnPromise)
            }
        }
      })
    } else {
      returnPromise.success(Seq.empty)
    }

    returnPromise.future
  }

  // Must be called within synchronized block
  private def appendResultAndCheckPromise[B](counter: Int, valueOpt: Option[B], returnSeq: ArrayBuffer[B], returnPromise: Promise[Seq[B]]) = {
    valueOpt.foreach(returnSeq += _)
    val ret = counter - 1
    if (ret == 0) {
      returnPromise.success(returnSeq)
    }
    ret
  }
}
