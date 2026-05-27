# x3d Map

![Discord](https://img.shields.io/discord/1370807259495534663?style=flat-square&logo=discord&label=xkball's%20mods&link=https%3A%2F%2Fdiscord.gg%2FS9DBXWHNsc) ![GitHub License](https://img.shields.io/github/license/xkball/x3dmap?style=flat-square)

A 3D terrain map mod for Minecraft. Provides a rotatable, zoomable 3D world map with terrain rendering, minimap overlay, and waypoint management.

本模组为 Minecraft 添加了一个可旋转、缩放的三维立体世界地图，支持地形渲染、小地图覆盖层和路径点管理。

## Demonstrate 效果展示

![3D Map](https://github.com/xkball/x3dmap/blob/master/img/3d_map.png)
![Performance](https://github.com/xkball/x3dmap/blob/master/img/3d_map_far.png)
![Minimap](https://github.com/xkball/x3dmap/blob/master/img/minimap.png)

## Feature List 功能列表

| Feature | Description |
| ------- | ----------- |
| 3D Terrain Map | Block-level high-detail terrain rendering with LOD system |
| Minimap | Configurable minimap HUD overlay with compass |
| Waypoints | Add, edit, delete, teleport, and share waypoints on the map |
| Chunk Selection | Box-select chunks for batch re-render, re-request, or delete operations |
| Picture-in-Picture | Render the 3D map in a PIP overlay |
| Compatibility Mode | Fallback mode for GPUs lacking MDI, sparse texture, or SSBO support |
| Server Chunk Request | Request server-side chunk re-sending (requires server permission) |
| Auto Save | Periodic auto-saving of map data |

| 功能 | 说明 |
| ---- | ---- |
| 3D 地形地图 | 带 LOD 系统的高精度方块级地形渲染 |
| 小地图 | 可配置的小地图 HUD 覆盖层，带指南针 |
| 路径点 | 在地图上添加、编辑、删除、传送和分享路径点 |
| 区块选择 | 框选区块以进行批量重渲染、重新请求或删除 |
| 画中画 | 在画中画覆盖层中渲染 3D 地图 |
| 兼容模式 | 为不支持 MDI、稀疏纹理或 SSBO 的 GPU 提供降级兼容模式 |
| 服务器区块请求 | 向服务器请求重新发送区块数据（需要服务器权限） |
| 自动保存 | 定期自动保存地图数据 |

## Controls 操作说明

| Action | Control |
| ------ | ------- |
| Open Map | Press `M` (configurable) |
| Move Map | Right-click drag / WASD |
| Rotate Map | Middle-click drag / Q / E |
| Zoom In/Out | Scroll wheel |
| Quick Add Waypoint | Double left-click on map |
| Add Waypoint (manual) | Click add waypoint button, then left-click on map |
| Select Chunks | Toggle selection mode, left-drag on map |

| 操作 | 按键 |
| ---- | ---- |
| 打开地图 | 按 `M` 键（可配置） |
| 移动地图 | 右键拖拽 / WASD |
| 旋转地图 | 中键拖拽 / Q / E |
| 缩放 | 鼠标滚轮 |
| 快速添加路径点 | 双击地图左键 |
| 手动添加路径点 | 点击添加路径点按钮，然后在地图上左键 |
| 选择区块 | 切换选择模式，左键拖拽 |

## Some Explanation 一些说明

- The 3D map does not support cave views.
- In non-compatibility mode, the map has high-detail block-level terrain and less detailed heightfield terrain. The map loads block-level terrain based on load distance and renders it based on LOD distance.
- When compatibility mode is active, severe frame drops or crashes may occur.
- Compatibility mode is automatically detected based on GPU capabilities; use `forceCompatibilityMode` to manually enable it.
- Server-side chunk re-sending (`Request Geomatics`) requires `allowServerSentChunk` to be set to `true` in the server config. Enabling this may cause server lag because it forces the server to load and send chunks on demand.
- 3D 地图不支持洞穴视图；
- 非兼容模式下，地图分为高精度的方块级别地形和较少细节的高度场地形，地图根据加载距离加载方块级别地形，根据 LOD 距离来渲染；
- 兼容模式下可能严重掉帧或崩溃；
- 兼容模式基于 GPU 能力自动检测，可通过 `forceCompatibilityMode` 手动启用；
- 服务端重新发送区块（"请求地理数据"按钮）需要在服务端配置文件中将 `allowServerSentChunk` 设置为 `true`，开启后可能造成服务端卡顿，因为服务器需要按需强制加载并发送区块。
