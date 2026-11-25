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

uniform int uRealistic;
uniform vec3 uSunDirection;
uniform mat4 uLightMatrix;
uniform sampler2DShadow uShadowMap;
uniform int uTranslucent;

// Minecraft's fixed face brightness: top 1.0, north and south 0.8, east and west 0.6, bottom 0.5.
float faceBrightness(vec3 n) {
    return min(n.x * n.x * 0.6 + n.y * n.y * (3.0 + n.y) / 4.0 + n.z * n.z * 0.8, 1.0);
}

// How much of the sun reaches a point, from 0 to 1, softened over nearby shadow-map texels.
float sunVisibility(vec3 world, vec3 offsetDirection) {
    vec4 light = uLightMatrix * vec4(world + offsetDirection * 0.06, 1.0);
    vec3 map = light.xyz / light.w * 0.5 + 0.5;
    if (map.x < 0.0 || map.x > 1.0 || map.y < 0.0 || map.y > 1.0) {
        return 1.0;
    }
    float depth = clamp(map.z, 0.0, 1.0);
    vec2 texel = 1.0 / vec2(textureSize(uShadowMap, 0));
    float total = 0.0;
    for (int x = -2; x <= 2; x++) {
        for (int y = -2; y <= 2; y++) {
            total += texture(uShadowMap, vec3(map.xy + vec2(x, y) * texel, depth));
        }
    }
    return total / 25.0;
}

// Sky light plus shadowed sun. A zero normal is a crossed plant, which is lit from both sides.
float realisticBrightness(vec3 world, vec3 normal) {
    if (dot(normal, normal) < 0.0001) {
        return 0.5 + 0.62 * sunVisibility(world, uSunDirection);
    }
    float sky = 0.34 + 0.2 * (normal.y * 0.5 + 0.5);
    float facing = max(dot(normal, uSunDirection), 0.0);
    return sky + (facing > 0.0 ? 0.82 * facing * sunVisibility(world, normal) : 0.0);
}

// Subsurface scattering for leaves and plants: sunlight passing through a thin leaf lights the side turned away from
// the sun, tinted warm by the leaf. The sun is looked up a block toward it, on the leaf's lit side. Zero without
// realistic lighting or for solid blocks.
vec3 transmittedLight(vec3 world, vec3 normal) {
    if (uRealistic != 1 || uTranslucent != 1) {
        return vec3(0.0);
    }
    float behind = dot(normal, normal) < 0.0001 ? 0.5 : max(-dot(normal, uSunDirection), 0.0);
    float light = behind * sunVisibility(world + uSunDirection * 1.2, uSunDirection);
    return 0.6 * light * vec3(1.1, 1.15, 0.7);
}
