// use an integer for version numbers
version = 4

cloudstream {
    language = "en"

    // Semua properti di bawah ini opsional, bisa dihapus jika tidak perlu
    description = "Example plugin by Hexated"
    authors = listOf("Hexated")

    /**
     * Status:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     */
    status = 1

    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime",
        "AsianDrama"
    )

    iconUrl = "https://moviebox.ph/favicon.ico"
}
