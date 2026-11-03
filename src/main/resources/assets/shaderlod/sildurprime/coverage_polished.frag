#version 330 core
// Sildur2.02 by Sildur; private same-frame native ALWAYS/SOLID polished10081 subset.
// Native alpha is not tested on this actual pass. Same native vertex object and TAA.
flat in int shaderlodSildurDepthMaterial;
void main(){if(shaderlodSildurDepthMaterial != 10081) discard;}
