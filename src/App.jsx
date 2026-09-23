import Header from './components/Header/Header'
import Footer from './components/Footer/Footer'
import Body from './components/Body/Body'
import './App.css'

function App() {
  return (
    <div className="app">
      <div className="salon-bg" aria-hidden="true" />
      <Header />
      <main>
        <Body />
      </main>
      <Footer />
    </div>
  )
}

export default App
