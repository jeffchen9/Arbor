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

in vec2 vNdc;

uniform mat4 uInverseViewProjection;
uniform vec3 uEye;
uniform vec3 uBoxMin;
uniform vec3 uBoxSize;
uniform float uThreshold;
uniform float uOpacity;
uniform float uStep;
uniform sampler3D uDensity;
uniform sampler2D uDepth;

out vec4 fragColor;

const int MAX_STEPS = 1200;

vec3 unproject(float ndcDepth) {
    vec4 point = uInverseViewProjection * vec4(vNdc, ndcDepth, 1.0);
    return point.xyz / point.w;
}

float dither() {
    return fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
}

void main() {
    vec3 ray = normalize(unproject(1.0) - uEye);
    vec3 inverseRay = 1.0 / (abs(ray) + vec3(1e-6)) * sign(ray + vec3(1e-9));
    vec3 toMin = (uBoxMin - uEye) * inverseRay;
    vec3 toMax = (uBoxMin + uBoxSize - uEye) * inverseRay;
    vec3 nearPlanes = min(toMin, toMax);
    vec3 farPlanes = max(toMin, toMax);
    float enter = max(max(nearPlanes.x, nearPlanes.y), max(nearPlanes.z, 0.0));
    float leave = min(farPlanes.x, min(farPlanes.y, farPlanes.z));
    if (leave <= enter) {
        discard;
    }

    float sceneDepth = texelFetch(uDepth, ivec2(gl_FragCoord.xy), 0).r;
    if (sceneDepth < 1.0) {
        leave = min(leave, length(unproject(sceneDepth * 2.0 - 1.0) - uEye));
    }
    if (leave <= enter) {
        discard;
    }

    vec3 color = vec3(0.0);
    float alpha = 0.0;
    float distanceAlong = enter + uStep * dither();
    for (int i = 0; i < MAX_STEPS && distanceAlong < leave && alpha < 0.985; i++) {
        vec3 position = uEye + ray * distanceAlong;
        float raw = texture(uDensity, (position - uBoxMin) / uBoxSize).r;
        if (raw > uThreshold) {
            float density = (raw - uThreshold) / (1.0 - uThreshold);
            float stepAlpha = 1.0 - exp(-density * uOpacity * uStep);
            float heightShade = clamp((position.y - uBoxMin.y) / max(uBoxSize.y, 1.0), 0.0, 1.0);
            vec3 leaf = mix(vec3(0.10, 0.30, 0.13), vec3(0.34, 0.62, 0.24), 0.35 * density + 0.65 * heightShade);
            color += (1.0 - alpha) * stepAlpha * leaf;
            alpha += (1.0 - alpha) * stepAlpha;
        }
        distanceAlong += uStep;
    }
    if (alpha <= 0.002) {
        discard;
    }
    fragColor = vec4(color, alpha);
}
