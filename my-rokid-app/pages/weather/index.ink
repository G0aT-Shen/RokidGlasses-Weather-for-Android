<script def>
{
  "navigationBarTitleText": "天气查询",
  "description": "查询天气相关问题优先使用此工具。支持查询中国大陆地区的实时天气、温度、湿度、风力、体感温度等信息。用户询问天气、气温、下雨、是否需要带伞等问题时自动调用。支持自动识别当前城市或用户指定的城市（如“北京天气怎么样”）。",
  "schema": {
    "data": {
      "type": "object",
      "properties": {
        "location": {
          "type": "string",
          "description": "目标城市名称（如“北京”、“上海”、“杭州”），仅支持城市名。若用户未明确指定城市，则默认为其当前所在城市。"
        }
      },
      "required": ["location"]
    }
  }
}
</script>

<script setup>
export default {
  data: {
    city: '',
    loading: true,
    weather: null,
    error: null
  },

  async onLoad(query) {
    const city = query.location || '杭州';

    this.setData({
      city,
      loading: true,
      error: null
    });

    try {
      const weather = await this.fetchWeather(city);
      this.setData({
        weather,
        loading: false
      });
    } catch (err) {
      this.setData({
        error: '天气数据获取失败，请稍后重试',
        loading: false
      });
    }
  },

  /** 中文城市名 → 英文（wttr.in 需要英文名） */
  cityToEnglish(name) {
    const map = {
      '北京': 'Beijing', '上海': 'Shanghai', '广州': 'Guangzhou', '深圳': 'Shenzhen',
      '杭州': 'Hangzhou', '成都': 'Chengdu', '武汉': 'Wuhan', '南京': 'Nanjing',
      '重庆': 'Chongqing', '天津': 'Tianjin', '苏州': 'Suzhou', '西安': 'Xi\'an',
      '长沙': 'Changsha', '郑州': 'Zhengzhou', '青岛': 'Qingdao', '大连': 'Dalian',
      '厦门': 'Xiamen', '福州': 'Fuzhou', '合肥': 'Hefei', '济南': 'Jinan',
      '哈尔滨': 'Harbin', '昆明': 'Kunming', '贵阳': 'Guiyang', '南宁': 'Nanning',
      '沈阳': 'Shenyang', '长春': 'Changchun', '太原': 'Taiyuan', '石家庄': 'Shijiazhuang'
    };
    return map[name] || name;
  },

  async fetchWeather(city) {
    // 使用 wttr.in 免费天气 API（无需注册，无需 API Key）
    const eng = this.cityToEnglish(city);
    const url = `https://wttr.in/${encodeURIComponent(eng)}?format=j1`;
    const res = await fetch(url);

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }

    const data = await res.json();
    const current = data.current_condition[0];

    // 解析当前天气
    const descEn = this.getDescValue(current.weatherDesc);
    const { condition, icon } = this.weatherToChinese(descEn);

    // 解析预报（今天 + 未来两天）
    const forecastLabels = ['明天', '后天', '大后天'];
    const forecast = data.weather.slice(1, 4).map((day, i) => {
      const midday = day.hourly[4] || day.hourly[0]; // 取中午12点左右的描述
      const dayDescEn = this.getDescValue(midday.weatherDesc);
      const dayInfo = this.weatherToChinese(dayDescEn);

      return {
        label: forecastLabels[i],
        icon: dayInfo.icon,
        high: parseInt(day.maxtempC),
        low: parseInt(day.mintempC)
      };
    });

    return {
      condition,
      icon,
      temperature: parseInt(current.temp_C),
      feelLike: parseInt(current.FeelsLikeC),
      humidity: parseInt(current.humidity),
      wind: `${this.windToChinese(current.winddir16Point)} ${current.windspeedKmph}km/h`,
      updateTime: this.getNow(),
      forecast
    };
  },

  /** 提取 weatherDesc 的第一个 value */
  getDescValue(arr) {
    return (arr && arr[0] && arr[0].value) || '';
  },

  /** 英文天气描述 → 中文 + emoji */
  weatherToChinese(desc) {
    const lower = desc.toLowerCase();

    if (lower.includes('thunder'))        return { condition: '雷阵雨', icon: '⛈️' };
    if (lower.includes('heavy rain') || lower.includes('torrential'))
                                           return { condition: '大雨', icon: '🌧️' };
    if (lower.includes('moderate rain'))  return { condition: '中雨', icon: '🌧️' };
    if (lower.includes('light rain') || lower.includes('patchy rain') || lower.includes('drizzle'))
                                           return { condition: '小雨', icon: '🌦️' };
    if (lower.includes('rain'))           return { condition: '雨', icon: '🌧️' };
    if (lower.includes('snow') || lower.includes('sleet') || lower.includes('ice'))
                                           return { condition: '雪', icon: '🌨️' };
    if (lower.includes('fog') || lower.includes('mist'))
                                           return { condition: '雾', icon: '🌫️' };
    if (lower.includes('overcast'))       return { condition: '阴', icon: '☁️' };
    if (lower.includes('cloudy'))         return { condition: '多云', icon: '⛅' };
    if (lower.includes('sunny') || lower.includes('clear'))
                                           return { condition: '晴', icon: '☀️' };

    return { condition: desc, icon: '🌤️' };
  },

  /** 英文风向 → 中文 */
  windToChinese(dir) {
    const map = {
      'N': '北风', 'NNE': '东北偏北风', 'NE': '东北风', 'ENE': '东北偏东风',
      'E': '东风', 'ESE': '东南偏东风', 'SE': '东南风', 'SSE': '东南偏南风',
      'S': '南风', 'SSW': '西南偏南风', 'SW': '西南风', 'WSW': '西南偏西风',
      'W': '西风', 'WNW': '西北偏西风', 'NW': '西北风', 'NNW': '西北偏北风'
    };
    return map[dir] || dir;
  },

  async onRefresh() {
    if (!this.data.city) return;

    this.setData({ loading: true, error: null });

    try {
      const weather = await this.fetchWeather(this.data.city);
      this.setData({
        weather: {
          ...weather,
          updateTime: this.getNow()
        },
        loading: false
      });
    } catch (err) {
      this.setData({
        error: '刷新失败，请稍后重试',
        loading: false
      });
    }
  },

  getNow() {
    const now = new Date();
    const h = String(now.getHours()).padStart(2, '0');
    const m = String(now.getMinutes()).padStart(2, '0');
    return `${h}:${m}`;
  },

};
</script>

<page>
  <view class="weather-card">
    <!-- 头部：城市 + 更新时间 -->
    <view class="card-header">
      <text class="city-name">{{ city }}</text>
      <text class="update-time" ink:if="{{ weather }}">{{ weather.updateTime }} 更新</text>
    </view>

    <!-- 加载状态 -->
    <view class="loading-area" ink:if="{{ loading }}">
      <text class="loading-text">正在查询天气...</text>
    </view>

    <!-- 错误状态 -->
    <view class="error-area" ink:elif="{{ error }}">
      <text class="error-text">😵 {{ error }}</text>
      <button class="retry-btn" bindtap="onRefresh">重试</button>
    </view>

    <!-- 天气信息 -->
    <view class="weather-main" ink:elif="{{ weather }}">
      <!-- 当前天气 -->
      <view class="current-weather">
        <text class="weather-icon">{{ weather.icon }}</text>
        <view class="weather-info">
          <view class="temp-row">
            <text class="temperature">{{ weather.temperature }}</text>
            <text class="unit">°C</text>
          </view>
          <text class="condition">{{ weather.condition }}</text>
        </view>
      </view>

      <!-- 详情指标 -->
      <view class="metrics">
        <view class="metric-item">
          <text class="metric-label">体感温度</text>
          <text class="metric-value">{{ weather.feelLike }}°</text>
        </view>
        <view class="metric-item">
          <text class="metric-label">湿度</text>
          <text class="metric-value">{{ weather.humidity }}%</text>
        </view>
        <view class="metric-item">
          <text class="metric-label">风力</text>
          <text class="metric-value">{{ weather.wind }}</text>
        </view>
      </view>

      <!-- 未来预报 -->
      <view class="forecast">
        <view
          class="forecast-item"
          ink:for="{{ weather.forecast }}"
          ink:key="label"
        >
          <text class="forecast-day">{{ item.label }}</text>
          <text class="forecast-icon">{{ item.icon }}</text>
          <text class="forecast-temp">{{ item.low }}° ~ {{ item.high }}°</text>
        </view>
      </view>

      <!-- 刷新按钮 -->
      <button class="refresh-btn" bindtap="onRefresh">
        <text>刷新天气</text>
      </button>
    </view>
  </view>
</page>

<style>
.weather-card {
  display: flex;
  flex-direction: column;
  padding: 20px;
  border-radius: 20px;
  background: linear-gradient(160deg, rgba(7, 193, 96, 0.18) 0%, rgba(7, 193, 96, 0.05) 60%, rgba(7, 193, 96, 0.02) 100%);
  border: 1px solid rgba(64, 255, 94, 0.12);
}

.card-header {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 16px;
}

.city-name {
  font-size: 22px;
  font-weight: 700;
  color: #ffffff;
}

.update-time {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
}

/* 加载状态 */
.loading-area {
  display: flex;
  justify-content: center;
  padding: 32px 0;
}

.loading-text {
  font-size: 15px;
  color: rgba(64, 255, 94, 0.7);
}

/* 错误状态 */
.error-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 20px 0;
}

.error-text {
  font-size: 15px;
  color: rgba(255, 255, 255, 0.6);
}

.retry-btn {
  color: #40FF5E;
  border: 1px solid rgba(64, 255, 94, 0.3);
  border-radius: 10px;
  padding: 6px 20px;
  background: transparent;
  font-size: 14px;
}

/* 当前天气 */
.current-weather {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
  padding: 16px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
}

.weather-icon {
  font-size: 48px;
  line-height: 48px;
}

.weather-info {
  display: flex;
  flex-direction: column;
}

.temp-row {
  display: flex;
  flex-direction: row;
  align-items: flex-start;
}

.temperature {
  font-size: 48px;
  font-weight: 300;
  color: #ffffff;
  line-height: 48px;
}

.unit {
  font-size: 18px;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 4px;
}

.condition {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.75);
  margin-top: 4px;
}

/* 指标区 */
.metrics {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-bottom: 18px;
  padding: 12px 14px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.04);
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.metric-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.metric-value {
  font-size: 16px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.85);
}

/* 预报区 */
.forecast {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-bottom: 18px;
}

.forecast-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.forecast-day {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.4);
}

.forecast-icon {
  font-size: 22px;
}

.forecast-temp {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.7);
}

/* 刷新按钮 */
.refresh-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 10px 0;
  border-radius: 12px;
  border: 1px solid rgba(64, 255, 94, 0.25);
  background: rgba(64, 255, 94, 0.06);
  color: #40FF5E;
  font-size: 15px;
}
</style>
