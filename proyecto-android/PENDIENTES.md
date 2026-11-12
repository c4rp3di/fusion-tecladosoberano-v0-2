# Pendientes del Teclado Soberano (para hacer de golpe)

## Sugerencias y aprendizaje
- [ ] Atajos definidos por el usuario (`dir` → dirección, `mail` → correo…): cifrados, editables desde la app del teclado.
- [ ] Modo opcional (apagado por defecto) que cuente también el uso de palabras del diccionario y suba las más escritas. Cifrado, con tope, apagado en contraseña/correo/URL, con botón para borrar.
- [ ] Predicción de la siguiente palabra (pares de palabras), opcional, mismas salvaguardas.
- [ ] Diccionario más grande: lista de 50.000–150.000 palabras por frecuencia (script de limpieza + índice para consultar sin cargarlo en memoria).

## Cuadro de corrección con fondo y letras claras
- Observado: según la app, al escribir mal aparece un cuadro de corrección con fondo claro y texto claro (ilegible).
- Diagnóstico: el teclado NO dibuja ese cuadro (no usa popups ni diálogos de corrección). Es el corrector ortográfico del sistema, que pinta la app (o MIUI) con su propio tema. Un teclado no puede cambiarle los colores.
- Opciones a valorar:
  - [ ] Correcciones propias en la barra de sugerencias, con los colores del tema del teclado (personalizables).
  - [ ] Ajustes en la app del teclado: colores del texto y fondo de la barra de sugerencias y avisos.
  - [ ] Comprobar en qué apps ocurre y si cambia con el modo oscuro del sistema o de la app.

## Otros
- [ ] Ajustes más completos al abrir la app (tema de la barra de sugerencias).

## Detectado en pruebas (captura del 11-nov)
- [ ] Se están aprendiendo erratas («Hjila» aparece como sugerencia). Mejorar: no aprender palabras muy parecidas (1-2 letras de diferencia) a una del diccionario, y exigir 2+ usos antes de sugerirlas.
- [ ] Corrección ortográfica propia: ante «Hjila» el corrector del sistema ofrece Huila/Hila/Hija, pero la barra del teclado no. Sugerir la corrección por cercanía al diccionario.

## Personalización del teclado en pantalla (pedido el 12-nov)
- [ ] Altura total del teclado (deslizador) y modo una mano (izquierda/derecha).
- [ ] Altura de cada fila por separado (incl. fila de números opcional).
- [ ] Ancho de teclas especiales: barra espaciadora, Shift, borrar, Intro (pesos editables).
- [ ] Editor visual en la app del teclado: vista previa en vivo con deslizadores; opción «restablecer».
- [ ] Márgenes entre teclas (más/menos separación) y tamaño de letra.
- [ ] Precisión al escribir (muchas erratas): zona de toque ampliada, corregir el solapamiento entre teclas vecinas, retraso de la pulsación larga ajustable (ahora 350 ms), pulsación de borrar con repetición ajustable, opción de que la tecla se escriba al soltar en vez de al pulsar.
- [ ] Guardar perfiles distintos para vertical y horizontal.

### Prioridad (aclarado el 12-nov)
- Las erratas son de LETRAS CERCANAS (la pulsación larga va bien: no tocar su tiempo). Escribe con las dos manos.
- Todo se edita desde la pantalla de la app del teclado (editor con vista previa en vivo).
- Orden: 1) zona de toque y precisión entre letras vecinas, 2) altura de teclado y filas, 3) ancho de espacio y teclas especiales, 4) separación/tamaño de letra, 5) perfiles vertical/horizontal.


---
## Hecho en v0.5 (13-nov)
- [x] Editor del teclado en la app: altura general y por fila, ancho de espacio y teclas especiales, separación, tamaño de letra, modo una mano, perfiles vertical/horizontal, restablecer. Se aplica al teclado real al instante.
- [x] Precisión: «imán» hacia la letra probable (ajustable) y decidir la tecla al tocar (por defecto) o al soltar.
- [x] Corrección propia en la barra («Hjila» -> «Hija»); no aprende erratas (palabras a 1 letra de una del diccionario necesitan 3 usos).
- [x] Siguiente palabra: pares de fábrica + aprendidos (opcional, apagado por defecto).
- [x] Recordar palabras frecuentes del diccionario (opcional, apagado por defecto).
- [x] Atajos de texto cifrados, con pantalla de gestión.
- [x] Colores del texto y del fondo de la barra de sugerencias.
- [x] Diccionario indexado (búsqueda binaria) e importable desde la app (.txt, hasta 150.000 palabras).
- [x] Marco: espacio vacío alrededor del teclado (general y por lado), en el editor.
- [x] Pulsación larga en una sugerencia la quita para siempre (se restaura desde ajustes). Los cambios de la app (atajos, borrar lo aprendido) llegan al teclado al instante.
