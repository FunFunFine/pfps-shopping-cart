package shop.config

import cats.effect._
import cats.implicits._
import ciris._
import ciris.refined._
import enumeratum._
import environments._
import environments.AppEnvironment._
import eu.timepit.refined.api._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import io.estatico.newtype.ops._
import scala.concurrent.duration._
import shop.config.data._

object load {

  // Ciris promotes configuration as code
  def apply[F[_]: Async: ContextShift]: F[AppConfig] =
    env("SC_APP_ENV")
      .as[AppEnvironment]
      .flatMap {
        case Test =>
          default(
            redisUri = "redis://localhost",
            paymentUri = "http://10.123.154.10/api"
          )
        case Prod =>
          default(
            redisUri = "redis://10.123.154.176",
            paymentUri = "https://payments.net/api"
          )
      }
      .load[F]

  private def default(
      redisUri: NonEmptyString,
      paymentUri: NonEmptyString
  ): ConfigValue[AppConfig] =
    (
      env("SC_JWT_SECRET_KEY").as[Secret[JwtSecretKeyConfig]],
      env("SC_JWT_CLAIM").as[Secret[JwtClaimConfig]],
      env("SC_ACCESS_TOKEN_SECRET_KEY").as[Secret[JwtSecretKeyConfig]],
      env("SC_ADMIN_USER_TOKEN").as[Secret[AdminUserTokenConfig]],
      env("SC_PASSWORD_SALT").as[Secret[PasswordSalt]]
    ).parMapN { (secretKey, claimStr, tokenKey, adminToken, salt) =>
      AppConfig(
        AdminJwtConfig(secretKey, claimStr, adminToken),
        tokenKey.coerce[TokenConfig],
        salt.coerce[PasswordConfig],
        30.minutes.coerce[TokenExpiration],
        30.minutes.coerce[ShoppingCartExpiration],
        CheckoutConfig(
          retriesLimit = 3,
          retriesBackoff = 10.milliseconds
        ),
        PaymentConfig(paymentUri),
        HttpClientConfig(
          connectTimeout = 2.seconds,
          requestTimeout = 2.seconds
        ),
        PostgreSQLConfig(
          host = "localhost",
          port = 5432,
          user = "postgres",
          database = "store",
          max = 10L
        ),
        RedisConfig(redisUri)
      )
    }

}
