<div align="center">

<img src="app/src/main/res/drawable/ic_banner.webp" style="border-radius: 24px; margin-top: 32px;"/>

# BV

~~Bug Video~~

[![GitHub Release Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fbadge.versions.bv.aaa1115910.dev%2Fgithub%3Fprerelease%3Dfalse)](https://github.com/aaa1115910/bv/releases?q=prerelease%3Afalse)
[![GitHub Release Pre-Release](https://img.shields.io/endpoint?url=https%3A%2F%2Fbadge.versions.bv.aaa1115910.dev%2Fgithub%3Fprerelease%3Dtrue)](https://github.com/aaa1115910/bv/releases?q=prerelease%3Atrue)

[![Workflow Release](https://github.com/aaa1115910/bv/actions/workflows/release.yml/badge.svg)](https://github.com/aaa1115910/bv/actions/workflows/release.yml)
[![Workflow Alpha](https://github.com/aaa1115910/bv/actions/workflows/alpha.yml/badge.svg)](https://github.com/aaa1115910/bv/actions/workflows/alpha.yml)
[![Android Sdk Require](https://img.shields.io/badge/Android-5.0%2B-informational?logo=android)](https://apilevels.com/#:~:text=Jetpack%20Compose%20requires%20a%20minSdk%20of%2021%20or%20higher)
[![GitHub](https://img.shields.io/github/license/aaa1115910/bv)](https://github.com/aaa1115910/bv)

**BV 不支持在中国大陆地区内使用，如有相关使用需求请使用 [云视听小电视](https://app.bilibili.com)**

</div>

---
BV ~~(Bug Video)~~ 是一款 [哔哩哔哩](https://www.bilibili.com) 的第三方 `Android TV`
应用，使用 `Jetpack Compose` 开发，支持 `Android 5.0+`

都是随心乱写的代码，能跑就行。

## 个人优化点

### 你懂的
### 界面优化：
- 首页改成动态
- ugc名称改成“分区”，pgc名称改成“影视”
- up主页和搜索结果卡片增加播放量和弹幕量
- 默认弹幕比例175，透明度70，弹幕区域50%（4K电视友好）
- 删除进入播放器左下角信息
- 优化视频中按下键显示的视频信息布局
### 功能优化：
- 存在历史记录时自动跳转到断点，按确认键从头播放
- 播放完成后自动退出播放器
- 快进快退自动播放，快退一次从5s改为10s，和快进保持一致
- 播放设置中播放速度单独列出，并改为固定挡位，退出视频后重置为1（删除原版的持久设置）
- 播放完成倒计时5秒再播放下一集

## r728 更新
- 播放设置中播放速度单独列出，改为0.5, 1, 1.25, 1.5, 2倍速挡位，退出视频后重置为1（删除原版的持久设置）
- 优化视频中按下键显示的视频信息布局

## Todo
- 增加点赞投币和一键三连

## License

[MIT](LICENSE) © aaa1115910
