#!/bin/bash

set -e

NAME=three.js-r185
/usr/bin/unzip -q ~/Downloads/${NAME}.zip ${NAME}/examples/models/\*

/usr/bin/mkdir -p downloads/threejs
/usr/bin/mv ${NAME}/examples/models/{3ds,3mf,bvh,collada,fbx,gltf,obj,ply,stl} \
    downloads/threejs

# cleanup:
/usr/bin/rm -rf ${NAME}
