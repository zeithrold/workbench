package doa.ink.workbench.service.identity.auth.support

import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

object OAuthAuthorizationCodeClient {
  private val formActionPattern =
    Pattern.compile("""<form[^>]*action="([^"]+)"""", Pattern.CASE_INSENSITIVE)
  private val hiddenInputPattern =
    Pattern.compile(
      """<input[^>]*type="hidden"[^>]*name="([^"]+)"[^>]*value="([^"]*)"""",
      Pattern.CASE_INSENSITIVE,
    )
  private val hiddenInputPatternAlt =
    Pattern.compile(
      """<input[^>]*value="([^"]*)"[^>]*type="hidden"[^>]*name="([^"]+)"""",
      Pattern.CASE_INSENSITIVE,
    )

  fun obtainAuthorizationCode(
    authorizationUrl: String,
    username: String,
    password: String,
    redirectUri: String,
  ): String {
    val cookieJar = CookieJar()
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()

    var currentUrl = authorizationUrl
    var response = get(client, currentUrl, cookieJar)
    repeat(15) {
      when {
        response.statusCode() in 300..399 -> {
          val location = resolveLocation(response, currentUrl)
          if (location.contains("code=")) {
            return extractQueryParam(location, "code")
          }
          currentUrl = location
          response = get(client, currentUrl, cookieJar)
        }
        response.statusCode() == 200 && response.body().contains("<form") -> {
          val (action, hiddenFields) = parseLoginForm(response.body())
          val loginAction = resolveUrl(action, currentUrl)
          response =
            postForm(
              client,
              loginAction,
              hiddenFields +
                mapOf(
                  "username" to username,
                  "password" to password,
                ),
              currentUrl,
              cookieJar,
            )
          currentUrl = loginAction
        }
        else ->
          error(
            "Unexpected OAuth authorize response: HTTP ${response.statusCode()} from $currentUrl"
          )
      }
    }

    if (response.statusCode() in 300..399) {
      val location = resolveLocation(response, currentUrl)
      if (location.startsWith(redirectUri) || location.contains("code=")) {
        return extractQueryParam(location, "code")
      }
    }

    error("Unable to obtain authorization code from identity provider.")
  }

  fun scopeQueryValue(authorizationUrl: String): String? {
    val query = URI.create(authorizationUrl).rawQuery ?: return null
    return query
      .split('&')
      .map { it.split('=', limit = 2) }
      .firstOrNull { it[0] == "scope" }
      ?.getOrNull(1)
      ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
  }

  private class CookieJar {
    private val cookies = linkedMapOf<String, String>()

    fun capture(response: HttpResponse<*>) {
      response.headers().allValues("Set-Cookie").forEach { header ->
        val cookiePair = header.substringBefore(';')
        val name = cookiePair.substringBefore('=')
        val value = cookiePair.substringAfter('=')
        if (name.isNotBlank() && value.isNotBlank()) {
          cookies[name] = value
        }
      }
    }

    fun headerValue(): String? =
      cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }.ifBlank { null }
  }

  private fun parseLoginForm(html: String): Pair<String, Map<String, String>> {
    val actionMatcher = formActionPattern.matcher(html)
    if (!actionMatcher.find()) {
      error("Login form action not found in IdP response.")
    }
    val action = actionMatcher.group(1).replace("&amp;", "&")
    val hiddenFields = linkedMapOf<String, String>()
    hiddenInputPattern.matcher(html).let { matcher ->
      while (matcher.find()) {
        hiddenFields[matcher.group(1)] = matcher.group(2).replace("&amp;", "&")
      }
    }
    hiddenInputPatternAlt.matcher(html).let { matcher ->
      while (matcher.find()) {
        hiddenFields.putIfAbsent(matcher.group(2), matcher.group(1).replace("&amp;", "&"))
      }
    }
    return action to hiddenFields
  }

  private fun get(client: HttpClient, url: String, cookieJar: CookieJar): HttpResponse<String> {
    val builder = HttpRequest.newBuilder().uri(URI.create(url)).GET()
    cookieJar.headerValue()?.let { builder.header("Cookie", it) }
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    cookieJar.capture(response)
    return response
  }

  private fun postForm(
    client: HttpClient,
    url: String,
    fields: Map<String, String>,
    referer: String,
    cookieJar: CookieJar,
  ): HttpResponse<String> {
    val body =
      fields.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
      }
    val builder =
      HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Referer", referer)
        .POST(HttpRequest.BodyPublishers.ofString(body))
    cookieJar.headerValue()?.let { builder.header("Cookie", it) }
    val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    cookieJar.capture(response)
    return response
  }

  private fun resolveLocation(response: HttpResponse<String>, baseUrl: String): String {
    val location =
      response.headers().firstValue("Location").orElseThrow {
        IllegalStateException("Redirect response missing Location header.")
      }
    return resolveUrl(location, baseUrl)
  }

  private fun resolveUrl(url: String, baseUrl: String): String {
    if (url.startsWith("http://") || url.startsWith("https://")) {
      return url
    }
    val base = URI.create(baseUrl)
    return base.resolve(url).toString()
  }

  private fun extractQueryParam(url: String, name: String): String {
    val query = url.substringAfter('?', "")
    return query
      .split('&')
      .map { it.split('=', limit = 2) }
      .firstOrNull { it[0] == name }
      ?.getOrNull(1)
      ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
      ?: error("Query parameter '$name' not found in redirect: $url")
  }

  private fun encode(value: String): String =
    java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
}
