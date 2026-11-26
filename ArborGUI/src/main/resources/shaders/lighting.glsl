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
uniform sampler3D uOcclusion;
uniform int uAmbientOcclusion;
uniform vec3 uOcclusionMin;
uniform vec3 uOcclusionSize;

// How much of the block around a point the tree fills, from 0 to 1, blended between neighbouring blocks.
float occupancy(vec3 world) {
    return texture(uOcclusion, (world - uOcclusionMin) / uOcclusionSize).r;
}

// Ambient occlusion: how open a point is to the sky, from 0.3 when buried in the crown to 1 in the open, judged from
// the blocks in three rings off its surface, nearer ones counting more. A zero normal, a crossed plant, looks upward.
float ambientOcclusion(vec3 world, vec3 normal) {
    if (uAmbientOcclusion != 1) {
        return 1.0;
    }
    vec3 n = dot(normal, normal) < 0.0001 ? vec3(0.0, 1.0, 0.0) : normal;
    vec3 t = normalize(cross(n, abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 b = cross(n, t);
    float blocked = 0.0;
    float total = 0.0;
    for (int ring = 0; ring < 3; ring++) {
        float distance = 0.75 + float(ring) * 1.1;
        float weight = 1.0 / (1.0 + float(ring));
        vec3 center = world + n * distance;
        blocked += weight * (2.0 * occupancy(center) + occupancy(center + t * distance)
                + occupancy(center - t * distance) + occupancy(center + b * distance) + occupancy(center - b * distance));
        total += weight * 6.0;
    }
    return 1.0 - 0.7 * smoothstep(0.0, 0.6, blocked / total);
}

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

// Sky light, darkened where the tree closes in, plus shadowed sun. A zero normal is a crossed plant, which is lit from
// both sides.
float realisticBrightness(vec3 world, vec3 normal) {
    float ambient = ambientOcclusion(world, normal);
    if (dot(normal, normal) < 0.0001) {
        return 0.5 * ambient + 0.62 * sunVisibility(world, uSunDirection);
    }
    float sky = (0.34 + 0.2 * (normal.y * 0.5 + 0.5)) * ambient;
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
