import { BarChart, Bar, LineChart, Line, PieChart, Pie, Cell, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid, Legend } from 'recharts'

const COLORS = ['#16a34a', '#2563eb', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6']

export default function ChartView({ chartData }) {
  if (!chartData) return null

  const formatLabel = (str) => {
    if (!str) return '';
    return str
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  };

  let chart_type, data, x_axis, y_axis, title

  if (chartData.type && chartData.x) {
    chart_type = chartData.type
    x_axis = 'name'
    y_axis = 'value'
    title = chartData.title || ''
    data = chartData.x.map((label, i) => ({
      name: label,
      value: chartData.y?.[i] ?? 0,
    }))
  } else {
    chart_type = chartData.chart_type
    data = chartData.data
    x_axis = chartData.x_axis
    y_axis = chartData.y_axis
    title = chartData.title || ''
  }

  if (!data || !data.length) return null

  const renderChart = () => {
    const formattedX = formatLabel(x_axis);
    const formattedY = formatLabel(y_axis);
    
    switch (chart_type) {
      case 'bar':
        return (
          <ResponsiveContainer width="100%" height={320}>
            <BarChart data={data} margin={{ top: 10, right: 10, left: 20, bottom: 25 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey={x_axis} height={80} tick={{ fontSize: 12, angle: -45, textAnchor: 'end', dy: 10 }} label={{ value: formattedX, position: 'bottom', offset: 0, fontSize: 12, fill: '#6b7280' }} />
              <YAxis tick={{ fontSize: 12 }} label={{ value: formattedY, angle: -90, position: 'insideLeft', offset: -10, fontSize: 12, fill: '#6b7280' }} />
              <Tooltip />
              <Legend verticalAlign="top" height={36} />
              <Bar dataKey={y_axis} name={formattedY} fill="#16a34a" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )
      case 'line':
        return (
          <ResponsiveContainer width="100%" height={320}>
            <LineChart data={data} margin={{ top: 10, right: 10, left: 20, bottom: 25 }}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey={x_axis} height={80} tick={{ fontSize: 12, angle: -45, textAnchor: 'end', dy: 10 }} label={{ value: formattedX, position: 'bottom', offset: 0, fontSize: 12, fill: '#6b7280' }} />
              <YAxis tick={{ fontSize: 12 }} label={{ value: formattedY, angle: -90, position: 'insideLeft', offset: -10, fontSize: 12, fill: '#6b7280' }} />
              <Tooltip />
              <Legend verticalAlign="top" height={36} />
              <Line type="monotone" dataKey={y_axis} name={formattedY} stroke="#16a34a" strokeWidth={2} />
            </LineChart>
          </ResponsiveContainer>
        )
      case 'pie':
        return (
          <ResponsiveContainer width="100%" height={320}>
            <PieChart>
              <Pie data={data} dataKey={y_axis} nameKey={x_axis} cx="50%" cy="50%" outerRadius={100} label>
                {data.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} />)}
              </Pie>
              <Tooltip formatter={(value, name) => [value, formatLabel(name)]} />
              <Legend layout="horizontal" verticalAlign="bottom" align="center" />
            </PieChart>
          </ResponsiveContainer>
        )
      default:
        return <p className="text-sm text-gray-500">Unsupported chart type: {chart_type}</p>
    }
  }

  return (
    <div className="bg-white dark:bg-neutral-800 border border-gray-200 dark:border-neutral-700 rounded-xl p-4 mb-3">
      {title && <h4 className="text-sm font-semibold text-gray-700 dark:text-neutral-300 mb-3">{title}</h4>}
      {renderChart()}
    </div>
  )
}
