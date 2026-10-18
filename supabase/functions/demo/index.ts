// Servidor de DEMOSTRACIÓN para CineCasa (imita la API Xtream).
// Solo contenido legal: películas de Blender Studio (Creative Commons) y clásicos de dominio público,
// con portadas de Wikimedia Commons. Usuario y contraseña: demo / demo.
const W = "https://upload.wikimedia.org/wikipedia/commons/";
const S = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/";
const IMG = S + "images/";

type M = { id: number; name: string; cat: string; poster: string; back?: string; year: number; rating: number; plot: string; genre: string; director: string; cast: string; mins: number; src: string; yt?: string };
const movies: M[] = [
  { id: 101, name: "Sintel (2010) [LAT]", cat: "1", poster: W + "8/8f/Sintel_poster.jpg", back: IMG + "Sintel.jpg", year: 2010, rating: 7.6, genre: "Animación, Fantasía, Aventura", director: "Colin Levy", cast: "Halina Reijn, Thom Hoffman", mins: 15, src: S + "Sintel.mp4", yt: "eRsGyueVLvQ", plot: "Una joven guerrera recorre tierras heladas y desiertos en busca del pequeño dragón que rescató y que le arrebataron. Lo que encuentra al final del camino cambiará todo." },
  { id: 102, name: "Sintel (2010) [SUB]", cat: "1", poster: W + "8/8f/Sintel_poster.jpg", back: IMG + "Sintel.jpg", year: 2010, rating: 7.6, genre: "Animación, Fantasía, Aventura", director: "Colin Levy", cast: "Halina Reijn, Thom Hoffman", mins: 15, src: S + "Sintel.mp4", yt: "eRsGyueVLvQ", plot: "Una joven guerrera recorre tierras heladas y desiertos en busca del pequeño dragón que rescató y que le arrebataron." },
  { id: 103, name: "Sintel (2010) [4K]", cat: "1", poster: W + "8/8f/Sintel_poster.jpg", back: IMG + "Sintel.jpg", year: 2010, rating: 7.6, genre: "Animación, Fantasía, Aventura", director: "Colin Levy", cast: "Halina Reijn, Thom Hoffman", mins: 15, src: S + "Sintel.mp4", yt: "eRsGyueVLvQ", plot: "Una joven guerrera recorre tierras heladas y desiertos en busca del pequeño dragón que rescató y que le arrebataron." },
  { id: 104, name: "Big Buck Bunny (2008) [LAT]", cat: "1", poster: W + "c/c5/Big_buck_bunny_poster_big.jpg", back: IMG + "BigBuckBunny.jpg", year: 2008, rating: 7.4, genre: "Animación, Comedia, Familia", director: "Sacha Goedegebure", cast: "", mins: 10, src: S + "BigBuckBunny.mp4", yt: "aqz-KE-bpKQ", plot: "Un conejo enorme y bonachón solo quiere disfrutar del bosque, hasta que tres roedores traviesos se meten con él. Es hora de una venganza muy creativa." },
  { id: 105, name: "Tears of Steel (2012) [SUB]", cat: "6", poster: W + "7/70/Tos-poster.png", back: IMG + "TearsOfSteel.jpg", year: 2012, rating: 6.8, genre: "Ciencia ficción, Acción", director: "Ian Hubert", cast: "Derek de Lint, Sergio Hasselbaink, Rogier Schippers", mins: 12, src: S + "TearsOfSteel.mp4", yt: "R6MlUcmOul8", plot: "En una Ámsterdam del futuro invadida por robots, un grupo de científicos intenta salvar el mundo reviviendo el recuerdo de un amor perdido." },
  { id: 106, name: "Elephants Dream (2006)", cat: "6", poster: W + "0/0c/ElephantsDreamPoster.jpg", back: IMG + "ElephantsDream.jpg", year: 2006, rating: 6.5, genre: "Animación, Ciencia ficción", director: "Bassam Kurdali", cast: "Tygo Gernandt, Cas Jansen", mins: 11, src: S + "ElephantsDream.mp4", plot: "Dos hombres recorren una máquina infinita y extraña. Uno cree que la entiende; el otro empieza a dudar de todo lo que le han enseñado." },
  { id: 107, name: "Cosmos Laundromat (2015)", cat: "1", poster: W + "c/c5/CosmosLaundromatPoster.jpg", back: IMG + "ForBiggerEscapes.jpg", year: 2015, rating: 7.2, genre: "Animación, Fantasía, Comedia", director: "Mathieu Auvray", cast: "Pierre Bokma, Reinout Scholten van Aschat", mins: 12, src: S + "ForBiggerEscapes.mp4", plot: "Una oveja cansada de la vida en una isla desolada recibe una oferta inesperada de un vendedor misterioso: la vida que siempre soñó." },
  { id: 108, name: "Spring (2019)", cat: "1", poster: W + "0/05/Spring2019PillarPosterBlender.jpg", back: IMG + "ForBiggerJoyrides.jpg", year: 2019, rating: 7.5, genre: "Animación, Fantasía", director: "Andy Goralczyk", cast: "", mins: 8, src: S + "ForBiggerJoyrides.mp4", plot: "Una pastora y su perro se enfrentan a los antiguos espíritus del bosque para que la primavera regrese a su valle." },
  { id: 201, name: "La quimera del oro (1925)", cat: "5", poster: W + "4/4d/Gold_rush_poster.jpg", year: 1925, rating: 8.1, genre: "Comedia, Aventura", director: "Charles Chaplin", cast: "Charles Chaplin, Georgia Hale", mins: 95, src: S + "ForBiggerFun.mp4", plot: "Charlot viaja a Alaska en plena fiebre del oro y sobrevive al hambre, al frío y a un oso, mientras se enamora de una bailarina." },
  { id: 202, name: "El chico (1921)", cat: "5", poster: W + "b/b5/The_Kid_%281921%29_poster.jpg", year: 1921, rating: 8.2, genre: "Comedia, Drama, Familia", director: "Charles Chaplin", cast: "Charles Chaplin, Jackie Coogan", mins: 68, src: S + "ForBiggerFun.mp4", plot: "Un vagabundo encuentra a un bebé abandonado y lo cría como suyo. Años después, las autoridades quieren separarlos." },
  { id: 203, name: "El maquinista de La General (1926)", cat: "5", poster: W + "0/0c/The_General_%281926%29_-_Movie_Poster_2.png", year: 1926, rating: 8.1, genre: "Comedia, Acción, Guerra", director: "Buster Keaton", cast: "Buster Keaton, Marion Mack", mins: 78, src: S + "ForBiggerBlazes.mp4", plot: "Un maquinista persigue a los espías que le robaron su amada locomotora... con su novia a bordo." },
  { id: 204, name: "El héroe del río (Sherlock Jr.) (1924)", cat: "5", poster: W + "c/c1/Sherlock_jr_poster.jpg", year: 1924, rating: 8.2, genre: "Comedia, Misterio", director: "Buster Keaton", cast: "Buster Keaton, Kathryn McGuire", mins: 45, src: S + "ForBiggerBlazes.mp4", plot: "Un proyeccionista que sueña con ser detective se mete, literalmente, dentro de la película que está proyectando." },
  { id: 205, name: "El hombre mosca (1923)", cat: "5", poster: W + "5/5e/Safety_last_poster.jpg", year: 1923, rating: 8.1, genre: "Comedia, Romance", director: "Fred C. Newmeyer", cast: "Harold Lloyd, Mildred Davis", mins: 70, src: S + "ForBiggerFun.mp4", plot: "Para impresionar a su novia, un empleado acepta escalar un rascacielos. La escena del reloj es una de las más famosas del cine." },
  { id: 206, name: "Viaje alucinante a Gulliver (1939)", cat: "1", poster: W + "7/7c/Gullivers_travel_poster_3.jpg", year: 1939, rating: 6.3, genre: "Animación, Aventura, Familia", director: "Dave Fleischer", cast: "", mins: 76, src: S + "ForBiggerJoyrides.mp4", plot: "Un náufrago despierta atado en Liliput, un reino de personas diminutas a punto de entrar en guerra por una canción de boda." },
  { id: 301, name: "Charada (1963)", cat: "3", poster: W + "f/fd/Charade_%281963_poster%29.jpg", year: 1963, rating: 7.8, genre: "Suspenso, Romance, Comedia", director: "Stanley Donen", cast: "Cary Grant, Audrey Hepburn", mins: 113, src: S + "ForBiggerMeltdowns.mp4", plot: "En París, una mujer descubre que su marido asesinado escondía una fortuna, y que varios hombres peligrosos la quieren de vuelta." },
  { id: 302, name: "Luna nueva (His Girl Friday) (1940)", cat: "5", poster: W + "2/21/His_Girl_Friday_%281940_poster%29_crop.jpg", year: 1940, rating: 7.8, genre: "Comedia, Romance", director: "Howard Hawks", cast: "Cary Grant, Rosalind Russell", mins: 92, src: S + "ForBiggerFun.mp4", plot: "Un editor de periódico hará lo que sea para que su exesposa, la mejor reportera de la ciudad, cubra una última gran noticia." },
  { id: 303, name: "El extraño (1946)", cat: "3", poster: W + "7/71/The_Stranger_%281946_film_poster%29.jpg", year: 1946, rating: 7.3, genre: "Suspenso, Cine negro", director: "Orson Welles", cast: "Orson Welles, Loretta Young, Edward G. Robinson", mins: 95, src: S + "ForBiggerMeltdowns.mp4", plot: "Un investigador persigue a un criminal de guerra que se esconde como profesor en un tranquilo pueblo de Connecticut." },
  { id: 304, name: "Desvío (Detour) (1945)", cat: "3", poster: W + "4/47/Detour_%28poster%29.jpg", year: 1945, rating: 7.2, genre: "Cine negro, Suspenso", director: "Edgar G. Ulmer", cast: "Tom Neal, Ann Savage", mins: 68, src: S + "ForBiggerMeltdowns.mp4", plot: "Un pianista que viaja de aventón a Los Ángeles queda atrapado en una cadena de malas decisiones de la que no puede escapar." },
  { id: 305, name: "D.O.A. Con las horas contadas (1950)", cat: "3", poster: W + "9/9e/D.O.A._%281950_poster%29.jpg", year: 1950, rating: 7.2, genre: "Cine negro, Misterio", director: "Rudolph Maté", cast: "Edmond O'Brien, Pamela Britton", mins: 83, src: S + "ForBiggerMeltdowns.mp4", plot: "Un hombre descubre que fue envenenado y que le quedan horas de vida. Las usará para encontrar a su propio asesino." },
  { id: 306, name: "El autoestopista (1953)", cat: "3", poster: W + "9/90/The_Hitch-Hiker_%281953_poster%29.jpeg", year: 1953, rating: 7.0, genre: "Suspenso, Cine negro", director: "Ida Lupino", cast: "Edmond O'Brien, Frank Lovejoy", mins: 71, src: S + "ForBiggerMeltdowns.mp4", plot: "Dos amigos en un viaje de pesca recogen a un desconocido en la carretera. Es un asesino buscado por la policía." },
  { id: 307, name: "Juan Nadie (1941)", cat: "5", poster: W + "3/38/Poster_-_Meet_John_Doe_01.jpg", year: 1941, rating: 7.6, genre: "Comedia, Drama", director: "Frank Capra", cast: "Gary Cooper, Barbara Stanwyck", mins: 122, src: S + "ForBiggerFun.mp4", plot: "Una reportera inventa una carta de un hombre común y contrata a un vagabundo para que la represente. El país entero le cree." },
  { id: 401, name: "La casa de la colina embrujada (1959)", cat: "4", poster: W + "2/24/House_on_Haunted_Hill.jpg", year: 1959, rating: 6.8, genre: "Terror, Misterio", director: "William Castle", cast: "Vincent Price, Carol Ohmart", mins: 75, src: S + "ForBiggerBlazes.mp4", plot: "Un millonario ofrece diez mil dólares a quien sobreviva una noche entera en una mansión con fama de estar maldita." },
  { id: 402, name: "Carnaval de almas (1962)", cat: "4", poster: W + "b/bd/Carnival_of_Souls_%281962_pressbook_cover%29.jpg", year: 1962, rating: 7.0, genre: "Terror, Misterio", director: "Herk Harvey", cast: "Candace Hilligoss", mins: 78, src: S + "ForBiggerBlazes.mp4", plot: "Tras sobrevivir a un accidente, una organista empieza a ver a un hombre pálido que la sigue a todas partes." },
  { id: 403, name: "El fantasma de la ópera (1925)", cat: "4", poster: W + "5/53/Phantom_of_the_opera_1925_poster.jpg", year: 1925, rating: 7.6, genre: "Terror, Drama", director: "Rupert Julian", cast: "Lon Chaney, Mary Philbin", mins: 93, src: S + "ForBiggerBlazes.mp4", plot: "Bajo la Ópera de París vive un genio enmascarado obsesionado con una joven cantante." },
  { id: 404, name: "El gabinete del doctor Caligari (1920)", cat: "4", poster: W + "5/52/Das_Cabinet_des_Dr._Caligari.JPG", year: 1920, rating: 8.0, genre: "Terror, Misterio", director: "Robert Wiene", cast: "Werner Krauss, Conrad Veidt", mins: 76, src: S + "ForBiggerBlazes.mp4", plot: "Un hipnotizador de feria y su sonámbulo llegan a un pueblo justo cuando empiezan los asesinatos." },
  { id: 405, name: "La cabeza que no quería morir (1962)", cat: "4", poster: W + "5/59/Brainthatwouldntdie_film_poster.jpg", year: 1962, rating: 4.5, genre: "Terror, Ciencia ficción", director: "Joseph Green", cast: "Jason Evers, Virginia Leith", mins: 82, src: S + "ForBiggerBlazes.mp4", plot: "Un cirujano mantiene viva la cabeza de su prometida tras un accidente y busca un cuerpo nuevo para ella." },
  { id: 501, name: "El mundo perdido (1925)", cat: "6", poster: W + "7/76/The_Lost_World_%281925%29_-_film_poster.jpg", year: 1925, rating: 6.6, genre: "Aventura, Ciencia ficción", director: "Harry O. Hoyt", cast: "Wallace Beery, Bessie Love", mins: 93, src: S + "ForBiggerJoyrides.mp4", plot: "Una expedición viaja al Amazonas en busca de una meseta donde aún viven los dinosaurios." },
  { id: 502, name: "Plan 9 del espacio exterior (1957)", cat: "6", poster: W + "b/bf/Plan_9_Alternative_poster.jpg", year: 1957, rating: 4.0, genre: "Ciencia ficción, Terror", director: "Ed Wood", cast: "Bela Lugosi, Vampira", mins: 79, src: S + "ForBiggerJoyrides.mp4", plot: "Extraterrestres reviven a los muertos para detener a la humanidad. Famosa por ser tan mala que es buena." },
  { id: 503, name: "El acorazado Potemkin (1925)", cat: "2", poster: W + "8/85/Vintage_Potemkin.jpg", year: 1925, rating: 7.9, genre: "Drama, Historia", director: "Serguéi Eisenstein", cast: "Aleksandr Antonov", mins: 75, src: S + "ForBiggerEscapes.mp4", plot: "Los marineros de un buque de guerra ruso se rebelan contra sus oficiales. Su escena de la escalinata cambió el cine." },
  { id: 504, name: "El terror (1963)", cat: "4", poster: W + "9/93/The_Terror_%281963%29_-_Poster.png", year: 1963, rating: 5.0, genre: "Terror, Misterio", director: "Roger Corman", cast: "Boris Karloff, Jack Nicholson", mins: 81, src: S + "ForBiggerBlazes.mp4", plot: "Un oficial perdido en la costa del Báltico sigue a una misteriosa mujer hasta el castillo de un barón atormentado." },
];
const vodCats = [
  { category_id: "1", category_name: "Animación", parent_id: 0 },
  { category_id: "6", category_name: "Ciencia ficción", parent_id: 0 },
  { category_id: "3", category_name: "Suspenso y cine negro", parent_id: 0 },
  { category_id: "4", category_name: "Terror clásico", parent_id: 0 },
  { category_id: "5", category_name: "Comedia clásica", parent_id: 0 },
  { category_id: "2", category_name: "Joyas del cine mudo", parent_id: 0 },
];

const series = [
  { series_id: 1001, name: "Caminandes", cat: "10", cover: W + "6/61/Pablo_Vazquez_-_Caminandes_-_Episode_1_-_Llama_Drama_-_Cover_thumbnail.png", back: W + "6/61/Pablo_Vazquez_-_Caminandes_-_Episode_1_-_Llama_Drama_-_Cover_thumbnail.png", year: 2013, rating: 7.4, genre: "Animación, Comedia, Familia", director: "Pablo Vázquez", plot: "Koro, una llama curiosa de la Patagonia, intenta cruzar al otro lado de la carretera... y del alambrado... y de todo lo que se le ponga enfrente.",
    eps: [["Drama de llama", S + "ForBiggerJoyrides.mp4"], ["Gran Dillama", S + "ForBiggerFun.mp4"], ["Llamigos", S + "ForBiggerEscapes.mp4"]] },
  { series_id: 1002, name: "Cortos de Blender Studio", cat: "10", cover: W + "0/0c/ElephantsDreamPoster.jpg", back: IMG + "ElephantsDream.jpg", year: 2006, rating: 7.3, genre: "Animación, Fantasía", director: "Blender Studio", plot: "Los cortometrajes abiertos más famosos del estudio holandés, uno por episodio.",
    eps: [["Elephants Dream", S + "ElephantsDream.mp4"], ["Big Buck Bunny", S + "BigBuckBunny.mp4"], ["Sintel", S + "Sintel.mp4"], ["Tears of Steel", S + "TearsOfSteel.mp4"]] },
];
const seriesCats = [{ category_id: "10", category_name: "Animación", parent_id: 0 }];

const live = [
  { id: 9001, name: "CineCasa Estrenos", cat: "20", icon: W + "8/8f/Sintel_poster.jpg", src: "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8" },
  { id: 9002, name: "Animación 24/7", cat: "20", icon: W + "c/c5/Big_buck_bunny_poster_big.jpg", src: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" },
  { id: 9003, name: "Ciencia ficción", cat: "20", icon: W + "7/70/Tos-poster.png", src: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" },
  { id: 9004, name: "Señal de prueba en vivo", cat: "21", icon: "", src: "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8" },
  { id: 9005, name: "Laboratorio HD", cat: "21", icon: "", src: "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8" },
  { id: 9006, name: "Cine Clásico", cat: "22", icon: W + "4/4d/Gold_rush_poster.jpg", src: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" },
  { id: 9007, name: "Noche de Terror", cat: "22", icon: W + "2/24/House_on_Haunted_Hill.jpg", src: "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8" },
  { id: 9008, name: "Aventura", cat: "22", icon: W + "7/76/The_Lost_World_%281925%29_-_film_poster.jpg", src: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8" },
];
const liveCats = [
  { category_id: "20", category_name: "Cine y animación", parent_id: 0 },
  { category_id: "21", category_name: "Pruebas en vivo", parent_id: 0 },
  { category_id: "22", category_name: "Clásicos", parent_id: 0 },
];

const json = (v: unknown) => new Response(JSON.stringify(v), { headers: { "content-type": "application/json; charset=utf-8", "access-control-allow-origin": "*" } });
const now = () => Math.floor(Date.now() / 1000);
const b64 = (s: string) => btoa(unescape(encodeURIComponent(s)));

Deno.serve((req) => {
  const u = new URL(req.url);
  const path = u.pathname.replace(/^.*?\/demo(?=\/|$)/, "");
  const q = u.searchParams;

  const m = path.match(/^\/(movie|series|live)\/[^/]+\/[^/]+\/(\d+)\.\w+$/);
  if (m) {
    const id = +m[2];
    let to = "";
    if (m[1] === "movie") to = movies.find((x) => x.id === id)?.src ?? S + "BigBuckBunny.mp4";
    else if (m[1] === "live") to = live.find((x) => x.id === id)?.src ?? live[0].src;
    else { for (const s of series) s.eps.forEach((e, i) => { if (s.series_id * 10 + i + 1 === id) to = e[1]; }); if (!to) to = S + "BigBuckBunny.mp4"; }
    return new Response(null, { status: 302, headers: { location: to } });
  }

  if (!/player_api\.php$/.test(path)) return json({ ok: true, demo: "CineCasa" });
  if ((q.get("username") ?? "") !== "demo" || (q.get("password") ?? "") !== "demo") return json({ user_info: { auth: 0 } });

  const action = q.get("action");
  switch (action) {
    case null: case "":
      return json({
        user_info: { username: "demo", password: "demo", auth: 1, status: "Active", exp_date: String(now() + 365 * 86400), is_trial: "0", active_cons: "0", max_connections: "3", allowed_output_formats: ["m3u8"] },
        server_info: { url: u.host, port: "443", https_port: "443", server_protocol: "https", timezone: "America/Tijuana", timestamp_now: now() },
      });
    case "get_vod_categories": return json(vodCats);
    case "get_series_categories": return json(seriesCats);
    case "get_live_categories": return json(liveCats);
    case "get_vod_streams":
      return json(movies.map((x, i) => ({ num: i + 1, name: x.name, stream_type: "movie", stream_id: x.id, stream_icon: x.poster, rating: String(x.rating), added: String(now() - i * 86400 * 3), category_id: x.cat, container_extension: "mp4", year: String(x.year) })));
    case "get_vod_info": {
      const x = movies.find((v) => String(v.id) === q.get("vod_id")) ?? movies[0];
      return json({ info: { movie_image: x.poster, backdrop_path: x.back ?? x.poster, plot: x.plot, cast: x.cast, director: x.director, genre: x.genre, releasedate: `${x.year}-01-01`, duration_secs: x.mins * 60, rating: String(x.rating), youtube_trailer: x.yt ?? "", country: "" }, movie_data: { stream_id: x.id, name: x.name, container_extension: "mp4" } });
    }
    case "get_series":
      return json(series.map((s) => ({ series_id: s.series_id, name: s.name, cover: s.cover, backdrop_path: s.back, plot: s.plot, cast: "", director: s.director, genre: s.genre, releaseDate: `${s.year}-01-01`, rating: String(s.rating), last_modified: String(now() - 86400), category_id: s.cat, youtube_trailer: "" })));
    case "get_series_info": {
      const s = series.find((v) => String(v.series_id) === q.get("series_id")) ?? series[0];
      return json({
        info: { name: s.name, cover: s.cover, backdrop_path: s.back, plot: s.plot, cast: "", director: s.director, genre: s.genre, releaseDate: `${s.year}-01-01`, rating: String(s.rating), youtube_trailer: "" },
        episodes: { "1": s.eps.map((e, i) => ({ id: String(s.series_id * 10 + i + 1), episode_num: i + 1, title: e[0], container_extension: "mp4", season: 1, info: { plot: `${s.name}, episodio ${i + 1}: ${e[0]}.`, movie_image: s.back, duration_secs: 600 } })) },
      });
    }
    case "get_live_streams":
      return json(live.map((c, i) => ({ num: i + 1, name: c.name, stream_type: "live", stream_id: c.id, stream_icon: c.icon, epg_channel_id: "demo" + c.id, category_id: c.cat, tv_archive: 0 })));
    case "get_short_epg": {
      const id = +(q.get("stream_id") ?? 0);
      const pool = movies.filter((x) => x.id < 300);
      const start = Math.floor(now() / 3600) * 3600;
      return json({ epg_listings: [0, 1, 2, 3].map((k) => { const x = pool[(id + k) % pool.length]; return { title: b64(x.name.replace(/\s*\[.*?\]/g, "")), description: b64(x.plot), start_timestamp: String(start + k * 3600), stop_timestamp: String(start + (k + 1) * 3600) }; }) });
    }
    default: return json([]);
  }
});
