package omniauth.lib

import omniauth.Omniauth

import net.liftweb._
import common._
import http.S
import json.JsonParser


import dispatch.classic._
import org.apache.http.HttpEntity

class DropboxProvider (val key:String, val secret:String) extends OmniauthProvider{
  implicit val formats = net.liftweb.json.DefaultFormats
  def providerName = DropboxProvider.providerName

  def callbackUrl = Omniauth.siteAuthBaseUrl+"auth/"+providerName+"/callback"
  
  def signIn() = {
    val baseReqUrl = "https://www.dropbox.com/oauth2/authorize?"
    val params = Map(
      "client_id" -> key,
      "response_type" -> "code",
      "redirect_uri" -> callbackUrl,
      "state" -> csrf
    )
    S.redirectTo(baseReqUrl + Omniauth.q_str(params))
  }
  
  def callback() = {
    execWithStateValidation {
      S.param("code") match {
        case Full(code) => {
          val req = :/("api.dropbox.com").secure / "oauth2/token" << Map(
            "code" -> code,
            "grant_type" -> "authorization_code",
            "redirect_uri" -> callbackUrl,
            "client_id" -> key,
            "client_secret" -> secret
          )
          
          val json = Omniauth.http(req >- JsonParser.parse)
          val token = AuthToken(
              (json \ "access_token").extract[String],
              None,
              None,
              None
            )

          if(validateToken(token)) {
            logger.debug("token validated")
            S.redirectTo(Omniauth.successRedirect)
          }
          else {
            logger.debug("token did not validate")
            S.redirectTo(Omniauth.failureRedirect)
          }
        }
        case _ => {
          logger.debug("code was not returned from Dropbox")
          S.redirectTo(Omniauth.failureRedirect)
        }

      }
    }
  }
  
  def validateToken(token: AuthToken) = {
    try {
      val (res, name, uid) = accountInfo(token)

      Omniauth.setAuthInfo(omniauth.AuthInfo(
        providerName,
        uid,
        name,
        token,
        Some(secret),
        Some(name)))
      
      true
    } catch {
      case e: Exception => false
    }
  }
  
  def tokenToId(token:AuthToken) = accountInfo(token) match {
    case (res, name, uid) => Full(uid)
    case _ => Empty
  }


  def accountInfo(accessToken:AuthToken) = {
    val req = ((:/("api.dropboxapi.com").secure / "2/users/get_current_account").POST <:< Map(
      "Authorization" -> ("Bearer " + accessToken.token)
    )).copy(body = None)
    val res = Omniauth.http(req >- JsonParser.parse)
    val name = (res \ "name" \ "display_name").extract[String]
    val uid = (res \ "account_id").extract[String]
    (res, name, uid)
  }
}

object DropboxProvider {
  val providerName = "dropbox"
  val providerPropertyKey = "omniauth.dropboxkey"
  val providerPropertySecret = "omniauth.dropboxsecret"
}