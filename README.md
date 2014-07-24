# probee2d

A Clojure library designed for 2D game developement

## Usage


### Window

To create a new window run

    (def w (create-window {:title "Test" :width 600 :height 600}))

the following functions can be called on a created window

    (show w) ; Makes the window visible on the screen
    (hide w) ; Hides the window from the screen without destroying it
    (change-window-size w {:width 800 :height 400}) ; Changes the size of the window
    (get-renderer w) ; Returns a Renderer used to draw on the window
    (render w) ; Renders the graphics draw by the Renderer
    (dispose w) ; Hides and destroys the window

all the functions returns a updated window record, with exception of dispose wich
returns nil

### Renderer

A renderer for a given window can be optained by the call

    (def r (get-renderer window))

it can then be used to clear or draw on the associated window.

To clear the window use

    (clear r :black)

### Image

To load a image from file run

    (def img (image "resources/filename.gif"))

or if you want to make transformations to the image at load time

    (def img (image "resources/filename.gif" {:transparent-color :white
                                              :rotate-angle 30
                                              :flip-direction :vertical
                                              :scale-factor 2}))

It is possible to transform an image after it is loaded by calling the appropiate function
from the eu.probee.probee2d.image namespace or by this call

    (transform img {:rotate-angle 90})

an image can be drawn to a window by calling

    (draw img renderer x y)

### Spritesheet

A spritesheet can be loaded by

    (def sh (spritesheet "resources/filename.png"))

and a Image can be extracted by calling

    (get sh index-x index-y)

where indexes starts at zero

You can apply transformations to the extracted image, while extracting it by adding a options map
to the call

    (get sh index-x index-y {:transparent-color :white})

## License

Copyright © 2014 probee

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
