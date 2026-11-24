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
    // Sunlit ground is pure white, so the shadow is the only thing visible on it.
    vec3 shadow = vec3(0.56, 0.59, 0.65);
    fragColor = vec4(mix(shadow, vec3(1.0), sunVisibility(vWorld, uNormal)), 1.0);
}
