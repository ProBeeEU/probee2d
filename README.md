# probee2d

A Clojure library designed for 2D game developement

## Usage

At the moment the only thing the lib can do is make a new window and resize it.

A new window is created like this

    (def w (create-window {:title "Test" :width 600 :height 600}))

And resized this way

    (change-window-size w {:width 800 :height 400})

## License

Copyright © 2014 probee

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
