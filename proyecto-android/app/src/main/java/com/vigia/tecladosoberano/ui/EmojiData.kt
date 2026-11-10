package com.vigia.tecladosoberano.ui

/**
 * Catálogo de emojis y caracteres especiales por categorías.
 * Al mostrarlos, EmojiPanel descarta los que el teléfono no sabe dibujar (así no salen cuadraditos).
 */
object EmojiData {

    class Category(val icon: String, val name: String, val items: List<String>)

    private fun parse(s: String): List<String> = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    val categories: List<Category> by lazy {
        listOf(
            Category("😀", "Caritas", parse("""
                😀 😃 😄 😁 😆 😅 😂 🤣 🙂 🙃 😉 😊 😇 🥰 😍 🤩 😘 😗 😚 😙 😋 😛 😜 🤪 😝 🤑 🤗 🤭 🤫 🤔
                🤐 🤨 😐 😑 😶 😏 😒 🙄 😬 🤥 😌 😔 😪 🤤 😴 😷 🤒 🤕 🤢 🤮 🤧 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓
                🧐 😕 😟 🙁 ☹️ 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈
                👿 💀 ☠️ 💩 🤡 👹 👺 👻 👽 👾 🤖 😺 😸 😹 😻 😼 😽 🙀 😿 😾 🙈 🙉 🙊
            """)),
            Category("👍", "Personas", parse("""
                👍 👎 👌 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ ✋ 🤚 🖐️ 🖖 👋 🤝 🙏 ✍️ 💪 👏 🙌 👐 🤲 🤜 🤛 ✊ 👊
                👀 👁️ 👅 👄 🧠 🦷 🦴 👂 👃 👣 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 👮 🕵️ 💂 👷 🤴 👸 🤵 👰 🤰 🎅
                🤶 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💆 💇 🚶 🏃 💃 🕺 👯 🧘 🤦 🤷 🙋 🙇 💁 🙅 🙆 🙍 🙎 👪 💑 💏
            """)),
            Category("🐶", "Naturaleza", parse("""
                🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋
                🐌 🐞 🐜 🕷️ 🦂 🐢 🐍 🦎 🐙 🦑 🦐 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🐘 🦏 🐪 🐫 🦒 🐃
                🐂 🐄 🐎 🐖 🐏 🐑 🐐 🦌 🐕 🐩 🐈 🐓 🦃 🕊️ 🐇 🐁 🐀 🐿️ 🌵 🎄 🌲 🌳 🌴 🌱 🌿 ☘️ 🍀 🍁 🍂 🍃
                🌺 🌻 🌹 🌷 🌼 🌸 💐 🍄 🌾 🌍 🌙 ⭐ 🌟 ☀️ ⛅ ☁️ 🌧️ ⛈️ ❄️ ☃️ 🔥 💧 🌊 🌈 ⚡
            """)),
            Category("🍔", "Comida", parse("""
                🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍈 🍒 🍑 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥒 🌶️ 🌽 🥕 🥔 🍠 🥐 🍞 🥖 🧀 🥚
                🍳 🥞 🥓 🥩 🍗 🍖 🌭 🍔 🍟 🍕 🥪 🌮 🌯 🥗 🍝 🍜 🍲 🍛 🍣 🍱 🥟 🍤 🍙 🍚 🍘 🍥 🍢 🍡 🍧 🍨
                🍦 🥧 🍰 🎂 🍮 🍭 🍬 🍫 🍿 🍩 🍪 🌰 🥜 🍯 🥛 🍼 ☕ 🍵 🥤 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🍾 🥄 🍴 🍽️
            """)),
            Category("⚽", "Actividades", parse("""
                ⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥅 ⛳ 🏹 🎣 🥊 🥋 ⛸️ 🎿 ⛷️ 🏂 🏋️ 🤸 🤼 🤺 🏇 🏊 🚴 🏆 🥇 🥈
                🥉 🏅 🎖️ 🎪 🎭 🎨 🎬 🎤 🎧 🎼 🎹 🥁 🎷 🎺 🎸 🎻 🎲 🎯 🎳 🎮 🎰 🧩
            """)),
            Category("🚗", "Viajes", parse("""
                🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🚚 🚛 🚜 🛵 🏍️ 🚲 🛴 🚂 🚆 🚄 🚈 🚊 ✈️ 🛫 🛬 🚀 🛸 🚁 ⛵ 🚤
                🛳️ ⚓ 🚧 ⛽ 🚦 🗺️ 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 ⛲ ⛺ 🏠 🏡 🏢 🏥 🏦 🏨 🏪 🏫 ⛪ 🕌 🌅 🌆 🌉 🌌
            """)),
            Category("💡", "Objetos", parse("""
                ⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 💾 💿 📷 📹 🎥 📞 ☎️ 📺 📻 ⏰ ⌛ 🔋 🔌 💡 🔦 🕯️ 💰 💳 💎 🔧 🔨 ⚙️
                🔫 💣 🔪 🛡️ 🔮 💊 💉 🔬 🔭 🚪 🛏️ 🚽 🚿 🛁 🔑 🗝️ 🔒 🔓 🎁 🎈 🎉 🎊 ✉️ 📦 📝 📁 📅 📌 📎
                ✂️ 🔍 📚 📖 💼
            """)),
            Category("❤️", "Símbolos", parse("""
                ❤️ 🧡 💛 💚 💙 💜 🖤 💔 ❣️ 💕 💞 💓 💗 💖 💘 💝 ✨ 💫 💥 💢 💦 💤 ✅ ❌ ❎ ❓ ❗ ‼️ ⚠️ 🚫
                ⛔ 🔞 ♻️ ➕ ➖ ➗ ✖️ ♾️ 💯 🔔 🔕 🎵 🎶 ➡️ ⬅️ ⬆️ ⬇️ ↔️ 🔄 🔝 🆗 🆕 🆓 🔴 🟠 🟡 🟢 🔵 🟣
                ⚫ ⚪ ⭕ ✔️ ☑️
            """)),
            Category("Ω", "Especiales", parse("""
                ¡ ¿ « » ‹ › “ ” „ ‘ ’ ‚ … – — · • ° § ¶ † ‡ ‰ ª º ¬ ¦ ´ ¨ ¯ ¸ © ® ™ € £ ¥ ¢ ¤ ₿ ₹ ₽ ₩
                ± × ÷ ≤ ≥ ≠ ≈ ∞ √ ∑ ∏ ∫ ∂ ∆ π Ω µ ¹ ² ³ ¼ ½ ¾ ← → ↑ ↓ ↔ ↕ ⇐ ⇒ ⇔ ✓ ✔ ✗ ✘ ★ ☆
                ♀ ♂ ♠ ♣ ♥ ♦ ♪ ♫ ☺ ☻ ☎ ✉ ✂ ✈ ☀ ☁ ☂ ☃ ✿ ❀ ❁ ◆ ◇ ■ □ ▲ △ ▼ ▽ ● ○ ◎
                á é í ó ú ü ñ Á É Í Ó Ú Ü Ñ à è ì ò ù â ê î ô û ä ë ï ö ç Ç ã õ å æ œ ß ø
            """))
        )
    }
}
