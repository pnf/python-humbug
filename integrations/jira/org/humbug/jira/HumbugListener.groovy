/*
* Copyright (c) 2013 Humbug, Inc
*/

package org.humbug.jira

import static com.atlassian.jira.event.type.EventType.*

import com.atlassian.jira.event.issue.AbstractIssueEventListener
import com.atlassian.jira.event.issue.IssueEvent

import java.util.logging.Level
import java.util.logging.Logger

import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.NameValuePair

class HumbugListener extends AbstractIssueEventListener {
    Logger LOGGER = Logger.getLogger(HumbugListener.class.getName());

    // The email address of one of the bots you created on your Humbug settings page.
    String humbugEmail = ""
    // That bot's API key.
    String humbugAPIKey = ""

    // What stream to send messages to. Must already exist.
    String humbugStream = "jira"

    // The base JIRA url for browsing
    String issueBaseUrl = "https://jira.COMPANY.com/browse/"

    // Your humbug domain, only change if you have a custom one
    String domain = ""

    @Override
    void workflowEvent(IssueEvent event) {
      processIssueEvent(event)
    }

    String processIssueEvent(IssueEvent event) {
      String author = event.user.displayName
      String issueId = event.issue.key
      String issueUrl = issueBaseUrl + issueId
      String issueUrlMd = String.format("[%s](%s)", issueId, issueBaseUrl + issueId)
      String title = event.issue.summary
      String subject = truncate(String.format("%s: %s", issueId, title), 60)
      String assignee = "no one"
      if (event.issue.assignee) {
        assignee = event.issue.assignee.name
      }
      String comment = "";
      if (event.comment) {
        comment = event.comment.body
      }

      String content;

      // Event types:
      // https://docs.atlassian.com/jira/5.0/com/atlassian/jira/event/type/EventType.html
      // Issue API:
      // https://docs.atlassian.com/jira/5.0/com/atlassian/jira/issue/Issue.html
      switch (event.getEventTypeId()) {
        case ISSUE_COMMENTED_ID:
          content = String.format("%s **updated** %s with comment:\n\n> %s",
                                  author, issueUrlMd, comment)
          break
        case ISSUE_CREATED_ID:
          content = String.format("%s **created** %s priority %s, assigned to **%s**: \n\n> %s",
                                  author, issueUrlMd, event.issue.priorityObject.name,
                                  assignee, title)
          break
        case ISSUE_ASSIGNED_ID:
          content = String.format("%s **reassigned** %s to **%s**",
                                  author, issueUrlMd, assignee)
          break
        case ISSUE_DELETED_ID:
          content = String.format("%s **deleted** %s!",
                                  author, issueUrlMd)
          break
        case ISSUE_RESOLVED_ID:
          content = String.format("%s **resolved** %s as %s:\n\n> %s",
                                  author, issueUrlMd, event.issue.resolutionObject.name,
                                  comment)
          break
        case ISSUE_CLOSED_ID:
          content = String.format("%s **closed** %s with resolution %s:\n\n> %s",
                                  author, issueUrlMd, event.issue.resolutionObject.name,
                                  comment)
          break
        case ISSUE_REOPENED_ID:
          content = String.format("%s **reopened** %s:\n\n> %s",
                                  author, issueUrlMd, comment)
          break
        default:
          return
      }

      sendStreamMessage(humbugStream, subject, content)
    }

    String post(String method, NameValuePair[] parameters) {
      PostMethod post = new PostMethod(humbugUrl(method))
      post.setRequestHeader("Content-Type", post.FORM_URL_ENCODED_CONTENT_TYPE)
      try {
        post.setRequestBody(parameters)
        HttpClient client = new HttpClient()
        client.executeMethod(post)
        String response = post.getResponseBodyAsString()
        if (post.getStatusCode() != HttpStatus.SC_OK) {
          String params = ""
          for (NameValuePair pair: parameters) {
              params += "\n" + pair.getName() + ":" + pair.getValue()
          }
          LOGGER.log(Level.SEVERE, "Error sending Humbug message:\n" + response + "\n\n" +
                                   "We sent:" + params)
        }
        return response;
      } catch (IOException e) {
        throw new RuntimeException(e)
      } finally {
        post.releaseConnection()
      }
    }

    String truncate(String string, int length) {
      if (string.length() < length) {
        return string
      }
      return string.substring(0, length - 3) + "..."
    }

    String sendStreamMessage(String stream, String subject, String message) {
      NameValuePair[] body = [new NameValuePair("api-key", humbugAPIKey),
                              new NameValuePair("email",   humbugEmail),
                              new NameValuePair("type",    "stream"),
                              new NameValuePair("to",      stream),
                              new NameValuePair("subject", subject),
                              new NameValuePair("content", message)]
      return post("send_message", body);
    }

    String humbugUrl(method) {
      String url = "humbughq.com"
      if (domain != "") {
        url = domain + "." + url
      }
      return "https://" + url + "/api/v1/" + method
    }
}
