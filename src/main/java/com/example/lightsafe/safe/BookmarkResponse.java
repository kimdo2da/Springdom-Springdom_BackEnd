package com.example.lightsafe.safe;

public record BookmarkResponse(

        Long id,

        String routeName,

        double startLatitude,

        double startLongitude,

        double endLatitude,

        double endLongitude,

        int safetyScore

) {

    public static BookmarkResponse from(
            Bookmark bookmark
    ) {
        return new BookmarkResponse(
                bookmark.getId(),
                bookmark.getRouteName(),
                bookmark.getStartLatitude(),
                bookmark.getStartLongitude(),
                bookmark.getEndLatitude(),
                bookmark.getEndLongitude(),
                bookmark.getSafetyScore()
        );
    }
}