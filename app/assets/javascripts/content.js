$(function() {
    var window_height = $(window).height(),
        content_height = window_height - 300;
    $('.msg-center').height(content_height);
});

$( window ).resize(function() {
    var window_height = $(window).height(),
        content_height = window_height - 300;
    $('.msg-center').height(content_height);
});