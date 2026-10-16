package com.mauricio.cinecasa.data

/**
 * Búsqueda flexible:
 *  - ignora acentos, mayúsculas y signos ("pelicula" encuentra "Película")
 *  - acepta palabras a medias ("spid man" encuentra "Spider-Man")
 *  - tolera errores de dedo ("piratas del caribr")
 *  - entiende años ("batman 2022")
 *  - busca también en reparto y dirección (actores)
 */
object SearchEngine {

    class Query(raw: String) {
        val norm = TextUtil.norm(raw)
        val tokens = norm.split(' ').filter { it.isNotEmpty() }
        val year: Int = tokens.firstOrNull { it.length == 4 && (it.toIntOrNull() ?: 0) in 1900..2100 }?.toInt() ?: 0
        val words = tokens.filter { !(it.length == 4 && it.toIntOrNull() == year) }
        val valid get() = norm.length >= 2
    }

    private fun tokenScore(q: String, t: String): Int = when {
        t == q -> 100
        t.startsWith(q) -> 80
        q.length >= 3 && t.contains(q) -> 45
        q.length >= 4 && fuzzy(q, t) -> 40
        else -> 0
    }

    private fun fuzzy(q: String, t: String): Boolean {
        val max = if (q.length >= 7) 2 else 1
        if (TextUtil.lev(q, t, max) <= max) return true
        // tolera error mientras se escribe: compara con el inicio de la palabra
        return t.length > q.length && TextUtil.lev(q, t.substring(0, q.length), max) <= max
    }

    /** Puntaje de un título (0 = no coincide). */
    fun scoreTitle(q: Query, titleNorm: String, year: Int): Int {
        if (q.year > 0 && year > 0 && year != q.year) return 0
        if (q.words.isEmpty()) return if (q.year > 0 && year == q.year) 50 else 0
        val qWords = q.words.joinToString(" ")
        if (titleNorm == qWords) return 1000
        var s = 0
        if (titleNorm.startsWith(qWords)) s += 400 else if (titleNorm.contains(qWords)) s += 250
        val tt = titleNorm.split(' ')
        var misses = 0
        for (w in q.words) {
            var best = 0
            for (t in tt) {
                val v = tokenScore(w, t)
                if (v > best) best = v
                if (best == 100) break
            }
            if (best == 0) misses++ else s += best
        }
        // todas las palabras deben aparecer (se perdona una si son 3 o más)
        if (misses > 0 && !(q.words.size >= 3 && misses == 1)) return 0
        if (q.year > 0 && year == q.year) s += 60
        return s - titleNorm.length / 8
    }

    /** ¿El texto de reparto/dirección contiene a la persona buscada? */
    fun matchPeople(q: Query, peopleNorm: String): Boolean {
        if (peopleNorm.isEmpty() || q.words.isEmpty() || q.norm.length < 4) return false
        if (peopleNorm.contains(q.words.joinToString(" "))) return true
        val tt = peopleNorm.split(' ')
        return q.words.all { w -> w.length >= 3 && tt.any { t -> t == w || (w.length >= 4 && (t.startsWith(w) || fuzzy(w, t))) } }
    }
}

data class SearchResults(
    val query: String = "",
    val movies: List<Vod> = emptyList(),
    val shows: List<Show> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val peopleMovies: List<Vod> = emptyList(),
    val peopleShows: List<Show> = emptyList(),
    val person: Tmdb.Person? = null,
    val personMovies: List<Vod> = emptyList(),
    val personShows: List<Show> = emptyList(),
) {
    val empty get() = movies.isEmpty() && shows.isEmpty() && channels.isEmpty() && peopleMovies.isEmpty() && peopleShows.isEmpty() && personMovies.isEmpty() && personShows.isEmpty()
}
