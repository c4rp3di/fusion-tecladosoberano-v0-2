package com.vigia.tecladosoberano.ui

import com.vigia.tecladosoberano.suggest.Dictionary

/**
 * Búsqueda de emojis por palabras en español. Todo local: es una tabla de palabras clave dentro del APK.
 * Cada término de la búsqueda debe ser el principio de alguna palabra clave (sin importar tildes ni mayúsculas).
 */
object EmojiSearch {

    private class Entrada(val emoji: String, val claves: List<String>)

    private val entradas: List<Entrada> by lazy {
        DATOS.trim().lines().mapNotNull { linea ->
            val i = linea.indexOf('|')
            if (i <= 0) null
            else Entrada(
                linea.substring(0, i).trim(),
                linea.substring(i + 1).trim().split(Regex("\\s+")).map { Dictionary.normalizar(it) }
            )
        }
    }

    fun buscar(consulta: String, max: Int = 60): List<String> {
        val terminos = consulta.trim().split(Regex("\\s+")).map { Dictionary.normalizar(it) }.filter { it.isNotEmpty() }
        if (terminos.isEmpty()) return emptyList()
        val salida = ArrayList<String>()
        for (e in entradas) {
            if (terminos.all { t -> e.claves.any { it.startsWith(t) } } && !salida.contains(e.emoji)) {
                salida.add(e.emoji)
                if (salida.size >= max) break
            }
        }
        return salida
    }

    private const val DATOS = """
😀|sonrisa feliz contento alegre cara
😃|sonrisa feliz alegre contento
😄|risa sonrisa feliz alegre
😁|sonrisa dientes feliz risa
😆|risa carcajada reir gracioso
😅|sudor nervios alivio risa uff
😂|risa llorar reir gracioso jaja lol
🤣|risa carcajada suelo reir jajaja
🙂|sonrisa suave ligera
🙃|reves ironia broma
😉|guino picardia coqueteo
😊|sonrojo timido feliz agradecido
😇|angel inocente santo bueno
🥰|amor enamorado corazones cariño
😍|amor ojos corazon enamorado encanta
🤩|asombro estrellas wow genial
😘|beso mua amor
😗|beso
😚|beso carino
😋|rico delicioso sabroso lengua comida
😛|lengua broma
😜|guino lengua broma loco
🤪|loco chiflado
😝|lengua asco broma
🤑|dinero rico codicia
🤗|abrazo cariño
🤭|risa timida ups secreto
🤫|silencio shh secreto callar
🤔|pensar duda hmm reflexion
🤐|callar boca cremallera secreto
🤨|duda sospecha ceja
😐|neutral serio indiferente
😑|inexpresivo aburrido
😶|mudo callado sin boca
😏|picaro arrogante suficiente
😒|desprecio aburrido molesto
🙄|ojos blanco aburrido harto fastidio
😬|nervios tension mueca
😌|alivio tranquilo paz
😔|triste pensativo decepcion
😪|sueño cansado dormir
🤤|baba antojo hambre ganas
😴|dormir sueño zzz cansado
😷|mascarilla enfermo gripe covid
🤒|fiebre enfermo termometro
🤕|herida venda daño
🤢|nauseas asco enfermo
🤮|vomitar asco
🤧|estornudo resfriado alergia
🥵|calor sudor
🥶|frio hielo helado
🥴|mareo borracho
😵|mareado desmayo
🤯|explotar mente flipar
🤠|vaquero cowboy
🥳|fiesta celebracion cumpleaños
😎|gafas guay chulo sol
🤓|empollon friki gafas listo
🧐|monoculo inspeccion curioso
😕|confuso duda
😟|preocupado
🙁|triste pena
😮|sorpresa asombro boca
😯|sorpresa silencio
😲|asombro sorpresa
😳|vergüenza sonrojo
🥺|suplica ruego tierno pena
😦|susto
😧|angustia
😨|miedo susto
😰|ansiedad sudor miedo
😥|alivio triste
😢|llorar lagrima triste
😭|llorar mucho llanto triste
😱|grito miedo terror
😖|agobio
😣|esfuerzo
😞|decepcion triste
😓|sudor frio agobio
😩|cansado harto
😫|agotado cansado
🥱|bostezo sueño aburrido
😤|enfado resoplar victoria
😡|enfado ira rabia enfadado
😠|enfado
🤬|insultos enfado maldecir
😈|diablo demonio travieso
👿|diablo demonio enfado
💀|calavera muerte muerto
☠️|calavera peligro muerte
💩|caca mierda
🤡|payaso
👹|ogro demonio
👻|fantasma halloween
👽|alien extraterrestre marciano
🤖|robot
🙈|mono no ver
🙉|mono no oir
🙊|mono no hablar
👍|bien vale ok pulgar genial
👎|mal no pulgar
👌|ok perfecto vale
✌️|paz victoria dos
🤞|suerte dedos cruzados
🤟|amor rock
🤘|rock cuernos
🤙|llamame telefono
👈|izquierda señalar
👉|derecha señalar
👆|arriba señalar
👇|abajo señalar
☝️|uno arriba
✋|mano parar stop alto
👋|hola adios saludo mano
🤝|trato acuerdo apreton manos
🙏|gracias rezar por favor suplicar
💪|fuerza musculo fuerte gimnasio
👏|aplauso bravo
🙌|celebrar hurra manos
👊|puño golpe
✊|puño fuerza
👀|ojos mirar
👅|lengua
👄|boca labios
🧠|cerebro mente inteligencia
👶|bebe
🧒|niño
👦|niño chico
👧|niña chica
👨|hombre
👩|mujer
👴|abuelo anciano
👵|abuela anciana
👮|policia
💃|baile bailar mujer
🕺|baile bailar hombre
🤦|facepalm vergüenza
🤷|ni idea dudar
🙋|levantar mano pregunta
🙇|reverencia perdon
🙅|no prohibido
🙆|ok vale
👪|familia
💑|pareja amor
💏|beso pareja
🐶|perro
🐱|gato
🐭|raton
🐰|conejo
🦊|zorro
🐻|oso
🐼|panda
🐨|koala
🐯|tigre
🦁|leon
🐮|vaca
🐷|cerdo cochino
🐸|rana sapo
🐵|mono
🐔|gallina pollo
🐧|pinguino
🐦|pajaro ave
🦆|pato
🦅|aguila
🦉|buho
🦇|murcielago
🐺|lobo
🐴|caballo
🦄|unicornio
🐝|abeja
🐛|gusano oruga
🦋|mariposa
🐌|caracol lento
🐞|mariquita
🐢|tortuga
🐍|serpiente culebra
🐙|pulpo
🦀|cangrejo
🐠|pez
🐟|pez pescado
🐬|delfin
🐳|ballena
🦈|tiburon
🐘|elefante
🦒|jirafa
🐑|oveja
🐕|perro
🐈|gato
🐇|conejo
🌵|cactus desierto
🎄|arbol navidad
🌳|arbol
🌴|palmera playa verano
🌱|planta brote
🍀|trebol suerte
🍁|hoja otoño
🌹|rosa flor amor
🌻|girasol flor
🌷|tulipan flor
🌸|flor cerezo primavera
💐|ramo flores
🍄|seta champiñon
🌍|mundo tierra planeta
🌙|luna noche
⭐|estrella
🌟|estrella brillo
☀️|sol dia calor
⛅|nubes sol
☁️|nube nublado
🌧️|lluvia
⛈️|tormenta rayo
❄️|nieve frio copo
☃️|muñeco nieve
🔥|fuego llama caliente
💧|gota agua
🌊|ola mar
🌈|arcoiris
⚡|rayo electricidad energia
🍎|manzana fruta
🍊|naranja fruta
🍋|limon
🍌|platano banana
🍉|sandia
🍇|uvas
🍓|fresa
🍒|cerezas
🍑|melocoton
🍍|piña
🥑|aguacate
🍅|tomate
🥕|zanahoria
🥔|patata
🌽|maiz
🌶️|picante guindilla
🍞|pan
🥐|cruasan desayuno
🧀|queso
🥚|huevo
🍳|huevo frito cocinar sarten
🥓|bacon beicon
🥩|carne filete
🍗|pollo muslo
🌭|perrito salchicha
🍔|hamburguesa
🍟|patatas fritas
🍕|pizza
🥪|sandwich bocadillo
🌮|taco
🥗|ensalada
🍝|pasta espaguetis
🍜|fideos sopa
🍲|guiso olla
🍣|sushi
🍱|bento caja
🍚|arroz
🍦|helado
🍰|tarta pastel
🎂|tarta cumpleaños
🍮|flan postre
🍭|piruleta caramelo
🍬|caramelo
🍫|chocolate
🍿|palomitas cine
🍩|donut rosquilla
🍪|galleta
☕|cafe taza
🍵|te infusion
🥤|refresco vaso
🍺|cerveza
🍻|cerveza brindis
🥂|brindis champan copas
🍷|vino
🥃|whisky copa
🍸|cocktail copa
🍹|cocktail
🍾|champan botella celebrar
🍴|cubiertos comer
⚽|futbol balon
🏀|baloncesto
🏈|futbol americano
⚾|beisbol
🎾|tenis
🏐|voleibol
🎱|billar
🏓|ping pong
🏆|trofeo copa campeon
🥇|oro primero medalla
🥈|plata segundo medalla
🥉|bronce tercero medalla
🎭|teatro mascaras
🎨|pintura arte paleta
🎬|cine pelicula
🎤|microfono cantar
🎧|auriculares musica
🎼|musica partitura
🎹|piano teclado
🎸|guitarra
🎻|violin
🥁|bateria tambor
🎲|dado juego
🎯|diana objetivo
🎮|videojuego mando
🧩|puzzle rompecabezas
🚗|coche auto
🚕|taxi
🚌|autobus
🏎️|carrera coche
🚓|policia coche
🚑|ambulancia
🚒|bomberos
🚚|camion
🏍️|moto
🚲|bicicleta bici
🚂|tren
✈️|avion viaje vuelo
🚀|cohete espacio
🚁|helicoptero
⛵|barco velero
⚓|ancla
🏠|casa hogar
🏢|oficina edificio
🏥|hospital
🏦|banco
🏨|hotel
🏫|colegio escuela
⛪|iglesia
🌅|amanecer atardecer
⌚|reloj
📱|movil telefono
💻|ordenador portatil
⌨️|teclado
📷|camara foto
🎥|camara video
📞|telefono llamar
☎️|telefono
📺|television tele
⏰|despertador alarma
⌛|reloj arena tiempo
🔋|bateria pila
💡|idea bombilla luz
💰|dinero bolsa
💳|tarjeta pago
💎|diamante joya
🔧|llave herramienta
🔨|martillo
⚙️|ajustes engranaje
💣|bomba
🔪|cuchillo
🔮|bola cristal magia
💊|pastilla medicina
💉|inyeccion vacuna
🔬|microscopio ciencia
🚪|puerta
🛏️|cama
🚽|baño inodoro
🚿|ducha
🔑|llave
🔒|candado cerrado seguridad
🔓|abierto candado
🎁|regalo
🎈|globo fiesta
🎉|fiesta confeti celebracion
🎊|fiesta confeti
✉️|sobre carta correo
📦|paquete caja
📝|nota apuntes escribir
📁|carpeta
📅|calendario fecha
📌|chincheta fijar
📎|clip adjunto
✂️|tijeras cortar
🔍|buscar lupa
📚|libros estudiar
📖|libro leer
💼|maletin trabajo
❤️|corazon amor rojo
🧡|corazon naranja
💛|corazon amarillo
💚|corazon verde
💙|corazon azul
💜|corazon morado
🖤|corazon negro
💔|corazon roto desamor
❣️|corazon exclamacion
💕|corazones amor
💞|corazones girando
💓|corazon latido
💗|corazon creciendo
💖|corazon brillante
💘|corazon flecha cupido
💝|corazon regalo lazo
✨|brillo magia chispas
💫|mareo estrellas
💥|explosion golpe
💢|enfado
💦|gotas sudor agua
💤|sueño dormir zzz
✅|correcto hecho vale
❌|error no cruz
❓|pregunta duda
❗|exclamacion atencion
⚠️|aviso peligro cuidado
🚫|prohibido
⛔|prohibido stop
♻️|reciclar
➕|mas suma
➖|menos resta
💯|cien perfecto
🔔|campana aviso notificacion
🔕|silencio campana
🎵|musica nota
🎶|musica notas
➡️|flecha derecha
⬅️|flecha izquierda
⬆️|flecha arriba
⬇️|flecha abajo
🔄|actualizar recargar
🔝|arriba top
🆗|ok
🆕|nuevo
🆓|gratis
🔴|rojo circulo
🟢|verde circulo
🔵|azul circulo
✔️|correcto check
"""
}
