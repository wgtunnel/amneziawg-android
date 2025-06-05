package junktag

import (
	"fmt"
	"regexp"
	"strings"
)

type Enum string

const (
	EnumBytes        Enum = "b"
	EnumCounter      Enum = "c"
	EnumTimestamp    Enum = "t"
	EnumRandomBytes  Enum = "r"
	EnumWaitTimeout  Enum = "wt"
	EnumWaitResponse Enum = "wr"
)

var validEnum = map[Enum]newGenerator{
	EnumBytes:        func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumCounter:      func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumTimestamp:    func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumRandomBytes:  func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumWaitTimeout:  func(s string) (Generator, error) { return &BytesGenerator{}, nil },
	EnumWaitResponse: func(s string) (Generator, error) { return &BytesGenerator{}, nil },
}

type Foo struct {
	x []Generator
}

type Tag struct {
	Name  Enum
	Param string
}

func parseTags(input string) ([]Tag, error) {
	// Regular expression to match <tagname optional_param>
	re := regexp.MustCompile(`([a-zA-Z]+)(?:\s+([^>]+))?>`)

	matches := re.FindAllStringSubmatch(input, -1)
	tags := make([]Tag, 0, len(matches))

	for _, match := range matches {
		tag := Tag{
			Name: Enum(match[1]),
		}
		if len(match) > 2 && match[2] != "" {
			tag.Param = strings.TrimSpace(match[2])
		}
		tags = append(tags, tag)
	}

	return tags, nil
}

func Parse(input string) (Foo, error) {
	inputSlice := strings.Split(input, "<")
	fmt.Printf("%v\n", inputSlice)
	if len(inputSlice) <= 1 {
		return Foo{}, fmt.Errorf("empty input: %s", input)
	}

	for _, inputParam := range inputSlice[1:] {
		if len(inputParam) == 1 {
			return Foo{}, fmt.Errorf("empty tag in input: %s", inputSlice)
		} else if strings.Count(inputParam, ">") != 1 {
			return Foo{}, fmt.Errorf("ill formated input: %s", input)
		}

		tags, _ := parseTags(inputParam)
		for _, tag := range tags {
			fmt.Printf("Tag: %s, Param: %s\n", tag.Name, tag.Param)
			gen, ok := validEnum[tag.Name]
			if !ok {
				return Foo{}, fmt.Errorf("invalid tag")
			}
			_, err := gen(tag.Param)
			if err != nil {
				return Foo{}, fmt.Errorf("")
			}
		}
	}

	return Foo{}, nil
}
