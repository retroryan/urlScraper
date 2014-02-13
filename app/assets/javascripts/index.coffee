$ ->
  $('#getMessageButton').click (event) =>
    jsRoutes.controllers.UrlController.getMessage().ajax
      context: this
      success:(data) ->
        $(".msg-center").append data.value + "<br>"

  $("#AddUrlBtn").click (event) =>
    scrapeUrl = $("#scrapeUrl").val()
    scrapeUrlJson = JSON.stringify({scrapeUrl: scrapeUrl})

    jsRoutes.controllers.UrlController.sendMessage().ajax
      data: scrapeUrlJson
      contentType: "application/json"
      success: (data) ->
        console.log(data)
        $(".msg-center").append data.value + "<br>"