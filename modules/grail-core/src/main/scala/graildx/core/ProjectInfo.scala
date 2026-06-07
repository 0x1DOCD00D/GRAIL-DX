package graildx.core

import cats.effect.IO
import cats.syntax.all.*
import org.typelevel.log4cats.noop.NoOpLogger

object ProjectInfo:
  def message: IO[String] =
    NoOpLogger[IO].info("Initialized GRAIL-DX").as("grail-dx")
