<div align="center">

<img src="app/src/main/res/drawable/ic_banner.webp" style="border-radius: 24px; margin-top: 32px;"/>

# BV

~~Bug Video~~

[![Android Sdk Require](https://img.shields.io/badge/Android-5.0%2B-informational?logo=android)](https://apilevels.com/#:~:text=Jetpack%20Compose%20requires%20a%20minSdk%20of%2021%20or%20higher)
[![GitHub](https://img.shields.io/github/license/aaa1115910/bv)](https://github.com/aaa1115910/bv)

**BV 不支持在中国大陆地区内使用，如有相关使用需求请使用 [云视听小电视](https://app.bilibili.com)**

</div>

---
BV ~~(Bug Video)~~ 是一款 [哔哩哔哩](https://www.bilibili.com) 的第三方 `Android TV`
应用，使用 `Jetpack Compose` 开发，支持 `Android 5.0+`

都是随心乱写的代码，能跑就行。

## 个人优化点

### 界面优化：
- 首页改为动态
- 左侧导航栏重做，不再作为抽屉展开
![首页](https://github.com/Frost819/picx-images-hosting/raw/master/Screenshot_20250630_162604.5c193jucfm.webp)
- 重写主页、分区页列表逻辑并简化了部分动画，大幅提升滚动流畅度
- up主页和搜索结果卡片增加播放量和弹幕量
- 大部分场景视频卡片增加投稿时间显示
- 默认弹幕比例175，透明度70，弹幕区域50%（4K电视友好）
- 删除进入播放器左下角信息
- 优化视频grid卡片标题显示（标题改为2行且宽度增加）
- 优化视频中按下键显示的视频信息显示
![播放器内信息](https://Frost819.github.io/picx-images-hosting/Screenshot_20250617_134623.sz7hvk7qv.webp)

### 功能优化：
- 你懂的
- 增加点赞和投币按钮，**长按点赞一键三连**
- 存在历史记录时自动跳转到断点，按确认键从头播放
- 播放完成后自动退出播放器
- 增加是否显示视频详情页设置项
- 快进快退自动播放，快退一次从5s改为10s，和快进保持一致
- 播放设置中播放速度单独列出，并改为固定挡位，退出视频后重置为1（删除原版的持久设置）
![播放速度设置页](https://Frost819.github.io/picx-images-hosting/Screenshot_20250617_134100.6m45r67018.webp)
- 播放完成倒计时5秒再播放下一集
- 视频详情页逻辑优化，不再优先显示合集
![视频详情页](https://github.com/Frost819/picx-images-hosting/raw/master/Screenshot_20250630_162642.1lc3ib5h87.webp)
- 主页、分区页、影视页内按菜单键可刷新视频列表（follow BBLL）
- bug fixes

## Update Notes
### 0.2.10 r728
- 播放设置中播放速度单独列出，改为0.5, 1, 1.25, 1.5, 2倍速挡位，退出视频后重置为1（删除原版的持久设置）
- 优化视频中按下键显示的视频信息布局

### 0.2.10 r730
- 增加点赞投币按钮和长按点赞一键三连
- 优化视频grid卡片标题显示（标题改为2行且宽度增加）

### 0.2.10 r732
- 新增是否显示视频详情页设置项（界面设置），关闭后点击视频直接播放
- 导航抽屉选项需按确定键才切换content，不再跟随focus切换（减少卡顿）

### 0.2.10 r733
- 优化视频详情页分P逻辑，不再优先显示合集
- 优化播放器内信息显示，时钟不再显示秒
- 合入原repo对无法播放h.265格式的bug fix

### 0.2.10 r737
- 主页导航栏重做，不再作为抽屉展开
- 大部分场景的视频卡片新增投稿时间（参考了@Leelion96的代码，感谢）
- ugc页面优化，删除无用的子分区
- 视频详情页不再显示模糊封面背景，提高性能
- 修复了播放器内选项页焦点问题导致的闪退bug
- 动态、热门、推荐页内按菜单键可刷新视频列表（follow BBLL）

### 0.2.10 r742
- 因小米电视屏蔽更换包名，需重新安装
- 重写主页、分区页列表逻辑，大幅提升滚动流畅度
- 动画简化
- 菜单键刷新功能同步到分区页和影视页
- web接口字幕错乱、主页导航栏焦点重置bug fix

### 0.3.0 r759
- 适配在线更新功能，基于github release
- merge多个原版0.3.0的更新，包括依赖更新和bug fix
- 父子焦点逻辑更新，预计降低闪退频率
- 修复web接口搜索无结果bug
- 修复小米电视首页刷新闪退
- 修复视频中按上键显示视频列表闪退

## Todo
- 播放完成行为设置，类似BBLL
- 默认首页设置
- 个人页独立
- 进度条下方快捷键，类似bbll

## License

[MIT](LICENSE) © aaa1115910
