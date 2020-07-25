import System.Console.GetOpt
import System.Environment
import Text.Regex
import Numeric (readSigned, readFloat)
import Text.Parsec hiding ((<|>))
import Text.Parsec.String
import qualified Text.Parsec.Token as P
import Control.Applicative hiding(many)
import Data.List
import Debug.Trace

-- create a lexer which knows how to skip C++ comments

import Text.Parsec.Language(javaStyle)
lexer = P.makeTokenParser javaStyle


symbol = P.symbol lexer     -- parses a known symbol (i.e. keyword)
identifier = P.identifier lexer  -- parses and gets an ident
integer = P.integer lexer

braces = P.braces lexer     -- parses {}
parens = P.parens lexer     -- parses ()
brackets = P.brackets lexer -- parses []

semi = P.semi lexer -- parses ;+whitespace
comma = P.comma lexer -- parses ,+whitespace
colon = P.colon lexer -- parses :+whitespace
commasep = P.commaSep lexer

dot = P.dot lexer

stringLiteral = P.stringLiteral lexer
whiteSpace = P.whiteSpace lexer


importparse = do
    symbol("import")
    identifier `sepBy1` dot
    semi
    
wrap x = [x]
paramparse = do
    x <- (wrap <$> many1 digit) <|> (identifier `sepBy1` dot)
    return(x)

depparse = do
    (char '@')
    x <- symbol "Deprecated"
    return x
    
matparse = do
    (Text.Parsec.optional depparse)
    x <- identifier
    parens (commasep $ paramparse)
    comma
    return (x)
    
classparse = do
    (symbol "public")
    (symbol "enum")
    (symbol "Material")
    (symbol "implements")
    (symbol "Keyed")
    char '{'
    whiteSpace
    x <- many matparse
    semi
    many anyToken
    return (x)
    
packparse = do
    (symbol "package")
    x <- identifier `sepBy1` dot
    semi
    return x
    
    
mainParser = do
    packparse
    many importparse
    x <- classparse
    return (x)


outmats x = 
    intercalate "\n" $ sort x
    

parser input = parse mainParser "unknown" input
    
main :: IO()
main = do
    args <- getArgs
    x <- readFile (args!!0)
    let q = parser x
    case q of
        (Right lst) ->
            putStrLn $ outmats lst
        (Left err) -> putStrLn $ show err
            
