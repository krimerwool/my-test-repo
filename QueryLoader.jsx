import { useState, useEffect } from 'react'

export default function QueryLoader() {
  const [timeElapsed, setTimeElapsed] = useState(0)
  const [dots, setDots] = useState('')

  useEffect(() => {
    // Timer to track seconds elapsed
    const timerInterval = setInterval(() => {
      setTimeElapsed(prev => prev + 1)
    }, 1000)

    // Timer for the animated dots (changes every 400ms)
    const dotsInterval = setInterval(() => {
      setDots(prev => {
        if (prev === '...') return ''
        return prev + '.'
      })
    }, 400)

    return () => {
      clearInterval(timerInterval)
      clearInterval(dotsInterval)
    }
  }, [])

  let text = 'Analyzing Query'
  if (timeElapsed >= 5 && timeElapsed <= 12) {
    text = 'Retrieving Context'
  } else if ((timeElapsed >= 13 && timeElapsed < 30) || timeElapsed > 32) {
    text = 'Generating Response'
  } else if (timeElapsed >= 30 && timeElapsed <= 32) {
    text = 'Retrying'
  }

  return (
    <div className="flex items-center space-x-3 p-4 bg-white dark:bg-neutral-800 rounded-2xl rounded-bl-sm border border-gray-100 dark:border-neutral-700 shadow-sm max-w-sm">
      <div className="flex space-x-1">
        <div className="w-2 h-2 bg-green-500 rounded-full animate-bounce" style={{ animationDelay: '0ms' }}></div>
        <div className="w-2 h-2 bg-green-500 rounded-full animate-bounce" style={{ animationDelay: '150ms' }}></div>
        <div className="w-2 h-2 bg-green-500 rounded-full animate-bounce" style={{ animationDelay: '300ms' }}></div>
      </div>
      <div className="text-sm font-medium text-gray-700 dark:text-gray-300 w-40 text-left">
        {text}{dots}
      </div>
    </div>
  )
}
