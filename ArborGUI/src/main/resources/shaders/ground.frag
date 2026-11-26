/*
 *     Arbor: Procedural Voxel Tree Generation.
 *     Copyright (C) 2026  Jeff Chen
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#version 330 core

in vec3 vWorld;

uniform vec3 uNormal;

out vec4 fragColor;

// @lighting

void main() {
    // Seen from below, the ground is see-through so the tree stays visible.
    if (!gl_FrontFacing) {
        discard;
    }
    // Sunlit ground is pure white, so the shadow is the only thing visible on it.
    vec3 shadow = vec3(0.56, 0.59, 0.65);
    vec3 lit = mix(shadow, vec3(1.0), sunVisibility(vWorld, uNormal));
    // The ground darkens softly where the tree stands on it and leans over it.
    fragColor = vec4(lit * mix(0.55, 1.0, ambientOcclusion(vWorld, uNormal)), 1.0);
}
